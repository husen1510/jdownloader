//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.hoster;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

import org.appwork.utils.formatter.SizeFormatter;

/*Same script for AbelhasPt, LolaBitsEs, CopiapopEs, MinhatecaComBr*/
@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "lolabits.es" }, urls = { "http://(www\\.)?lolabitsdecrypted\\.es/\\d+" }, flags = { 2 })
public class LolaBitsEs extends PluginForHost {

    public LolaBitsEs(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://lolabits.es/action/Registration/Create");
    }

    @Override
    public String getAGBLink() {
        return "http://lolabits.es/TerminosCondiciones.aspx";
    }

    private static boolean pluginloaded = false;

    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        if (link.getBooleanProperty("offline", false)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (!link.getDownloadURL().matches("http://(www\\.)?lolabitsdecrypted\\.es/\\d+")) {
            /* Only accept new links added via decrypter */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getStringProperty("mainlink", null));
        if (br.containsHTML("class=\"noFile\"")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String filename = link.getStringProperty("plain_filename", null);
        final String filesize = link.getStringProperty("plain_filesize", null);
        link.setFinalFileName(filename);
        link.setDownloadSize(SizeFormatter.getSize(filesize));
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        String dllink = null;
        int maxChunks = 0;
        try {
            dllink = get_dllink(downloadLink);
        } catch (final Throwable e) {
            logger.info("Failed to get downloadlink without account");
        }
        if (dllink == null) {
            /* Free users can only download low quality streams */
            dllink = br.getRegex("\"(http://(www\\.)?lolabits\\.es/Video\\.ashx\\?e=[^<>\"]*?)\"").getMatch(0);
            if (dllink == null) {
                try {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
                } catch (final Throwable e) {
                    if (e instanceof PluginException) {
                        throw (PluginException) e;
                    }
                }
                throw new PluginException(LinkStatus.ERROR_FATAL, "This file can only be downloaded by registered users");
            }
            dllink = Encoding.htmlDecode(dllink);
            maxChunks = 1;
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, maxChunks);
        if (dl.getConnection().getContentType().contains("html")) {
            /* Whatever happens here, always show server error... */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 30 * 60 * 1000l);
        }
        String contentDisposition = dl.getConnection().getHeaderField("Content-Disposition");
        if (contentDisposition == null || !contentDisposition.contains("filename")) {
            fixFilename(downloadLink);
        }
        dl.startDownload();
    }

    private static final String MAINPAGE = "http://lolabits.es";
    private static Object       LOCK     = new Object();

    @SuppressWarnings("unchecked")
    private void login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            try {
                // Load cookies
                br.setCookiesExclusive(true);
                final Object ret = account.getProperty("cookies", null);
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) {
                    acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                }
                if (acmatch && ret != null && ret instanceof HashMap<?, ?> && !force) {
                    final HashMap<String, String> cookies = (HashMap<String, String>) ret;
                    if (account.isValid()) {
                        for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            br.setCookie(MAINPAGE, key, value);
                        }
                        return;
                    }
                }
                br.setFollowRedirects(false);
                br.getPage("http://lolabits.es/");
                br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                br.postPage("http://lolabits.es/action/login/loginWindow", "irect=true");
                br.postPage("http://lolabits.es/action/login/login", "RememberMe=true&__RequestVerificationToken=undefined&RedirectUrl=&Redirect=True&FileId=0&Login=" + Encoding.urlEncode(account.getUser()) + "&Password=" + Encoding.urlEncode(account.getPass()));
                if (br.getCookie(MAINPAGE, "RememberMe") == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                // Save cookies
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = br.getCookies(MAINPAGE);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
            } catch (final PluginException e) {
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        try {
            login(account, true);
        } catch (PluginException e) {
            account.setValid(false);
            throw e;
        }
        ai.setUnlimitedTraffic();
        account.setValid(true);
        ai.setStatus("Registered (free) user");
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        login(account, false);
        br.setFollowRedirects(false);
        br.getPage(link.getStringProperty("mainlink", null));
        final String dllink = get_dllink(link);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        String contentDisposition = dl.getConnection().getHeaderField("Content-Disposition");
        if (contentDisposition == null || !contentDisposition.contains("filename")) {
            fixFilename(link);
        }
        dl.startDownload();
    }

    private String get_dllink(final DownloadLink dl) throws IOException, PluginException {
        /* First check if the id was set in the decrypter */
        String fileid = dl.getStringProperty("plain_fid", null);
        if (fileid == null) {
            fileid = br.getRegex("id=\"fileDetails_(\\d+)\"").getMatch(0);
        }
        if (fileid == null) {
            fileid = br.getRegex("fileIdContainer\"\\s*rel=\"(\\d+)\"").getMatch(0);
        }
        final String requestvtoken = br.getRegex("name=\"__RequestVerificationToken\" type=\"hidden\" value=\"([^<>\"]*?)\"").getMatch(0);
        if (fileid == null || requestvtoken == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        Browser br2 = br.cloneBrowser();
        br2.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br2.postPage("/action/License/Download", "fileId=" + fileid + "&__RequestVerificationToken=" + Encoding.urlEncode(requestvtoken));
        String dllink = br2.getRegex("\"redirectUrl\":\"(http[^<>\"]*?)\"").getMatch(0);
        if (dllink == null) {
            // general failover
            dllink = br2.getRegex("(https?://\\w+\\.lolabits\\.es/File\\.aspx\\?[^\"]+)(\\\\\")?").getMatch(0);
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        dllink = unescape(dllink);
        return dllink;
    }

    private static synchronized String unescape(final String s) {
        /* we have to make sure the youtube plugin is loaded */
        if (pluginloaded == false) {
            final PluginForHost plugin = JDUtilities.getPluginForHost("youtube.com");
            if (plugin == null) {
                throw new IllegalStateException("youtube plugin not found!");
            }
            pluginloaded = true;
        }
        return jd.plugins.hoster.Youtube.unescape(s);
    }

    private void fixFilename(final DownloadLink downloadLink) {
        String orgName = null;
        String orgExt = null;
        String servName = null;
        String servExt = null;
        String orgNameExt = downloadLink.getFinalFileName();
        if (orgNameExt == null) {
            orgNameExt = downloadLink.getName();
        }
        if (!inValidate(orgNameExt) && orgNameExt.contains(".")) {
            orgExt = orgNameExt.substring(orgNameExt.lastIndexOf("."));
        }
        if (!inValidate(orgExt)) {
            orgName = new Regex(orgNameExt, "(.+)" + orgExt).getMatch(0);
        } else {
            orgName = orgNameExt;
        }
        // if (orgName.endsWith("...")) orgName = orgName.replaceFirst("\\.\\.\\.$", "");
        String servNameExt = ".mp4";
        if (!inValidate(servNameExt) && servNameExt.contains(".")) {
            servExt = servNameExt.substring(servNameExt.lastIndexOf("."));
            servName = new Regex(servNameExt, "(.+)" + servExt).getMatch(0);
        }
        String FFN = null;
        if (inValidate(orgExt) && !inValidate(servExt) && (servName.toLowerCase().contains(orgName.toLowerCase()) && !servName.equalsIgnoreCase(orgName))) {
            /* when partial match of filename exists. eg cut off by quotation mark miss match, or orgNameExt has been abbreviated by hoster */
            FFN = servNameExt;
        } else if (!inValidate(orgExt) && !inValidate(servExt) && !orgExt.equalsIgnoreCase(servExt)) {
            FFN = orgName + servExt;
        } else {
            FFN = orgNameExt;
        }
        downloadLink.setFinalFileName(FFN);
    }

    /**
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     * 
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     * */
    private boolean inValidate(final String s) {
        if (s == null || s != null && (s.matches("[\r\n\t ]+") || s.equals(""))) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}