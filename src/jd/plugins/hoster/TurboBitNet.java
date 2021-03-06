//    jDownloader - Downloadmanager
//    Copyright (C) 2012  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
package jd.plugins.hoster;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.config.SubConfiguration;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.JDHexUtils;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.net.HTTPHeader;
import org.appwork.utils.os.CrossSystem;

//When adding new domains here also add them to the turbobit.net decrypter (TurboBitNetFolder)
@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "turbobit.net" }, urls = { "http://(www\\.)?(maxisoc\\.ru|turo\\-bit\\.net|depositfiles\\.com\\.ua|dlbit\\.net|sharephile\\.com|filesmail\\.ru|hotshare\\.biz|bluetooths\\.pp\\.ru|speed-file\\.ru|turbobit\\.pl|dz-files\\.ru|file\\.alexforum\\.ws|file\\.grad\\.by|file\\.krut-warez\\.ru|filebit\\.org|files\\.best-trainings\\.org\\.ua|files\\.wzor\\.ws|gdefile\\.ru|letitshare\\.ru|mnogofiles\\.com|share\\.uz|sibit\\.net|turbo-bit\\.ru|turbobit\\.net|upload\\.mskvn\\.by|vipbit\\.ru|files\\.prime-speed\\.ru|filestore\\.net\\.ru|turbobit\\.ru|upload\\.dwmedia\\.ru|upload\\.uz|xrfiles\\.ru|unextfiles\\.com|e-flash\\.com\\.ua|turbobax\\.net|zharabit\\.net|download\\.uzhgorod\\.name|trium-club\\.ru|alfa-files\\.com|turbabit\\.net|filedeluxe\\.com|turbobit\\.name|files\\.uz\\-translations\\.uz|turboblt\\.ru|fo\\.letitbook\\.ru|freefo\\.ru|bayrakweb\\.com|savebit\\.net|filemaster\\.ru|файлообменник\\.рф|vipgfx\\.net|turbovit\\.com\\.ua|turboot\\.ru)/([A-Za-z0-9]+(/[^<>\"/]*?)?\\.html|download/free/[a-z0-9]+|/?download/redirect/[A-Za-z0-9]+/[a-z0-9]+)" }, flags = { 2 })
public class TurboBitNet extends PluginForHost {

    private static final String RECAPTCHATEXT     = "api\\.recaptcha\\.net";
    private static final String CAPTCHAREGEX      = "\"(http://turbobit\\.net/captcha/.*?)\"";
    private static final String MAINPAGE          = "http://turbobit.net";
    private static Object       LOCK              = new Object();
    private static final String BLOCKED           = "Turbobit.net is blocking JDownloader: Please contact the turbobit.net support and complain!";

    private static final String NICE_HOST         = "turbobit.net";
    private static final String NICE_HOSTproperty = "turbobitnet";

    public TurboBitNet(final PluginWrapper wrapper) {
        super(wrapper);
        setConfigElements();
        enablePremium("http://turbobit.net/turbo");
    }

    @Override
    public boolean checkLinks(final DownloadLink[] urls) {
        if (urls == null || urls.length == 0) {
            return false;
        }
        // correct link stuff goes here, stable is lame!
        for (DownloadLink link : urls) {
            if (link.getProperty("LINKUID") == null) {
                try {
                    // standard links turbobit.net/uid.html &&
                    // turbobit.net/uid/filename.html
                    String uid = new Regex(link.getDownloadURL(), "https?://[^/]+/([a-z0-9]+)(/[^/]+)?\\.html").getMatch(0);
                    if (uid == null) {
                        // download/free/
                        uid = new Regex(link.getDownloadURL(), "download/free/([a-z0-9]+)").getMatch(0);
                        if (uid == null) {
                            // support for public premium links
                            uid = new Regex(link.getDownloadURL(), "download/redirect/[A-Za-z0-9]+/([a-z0-9]+)").getMatch(0);
                            if (uid == null) {
                                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                            }
                        }
                    }
                    if (link.getDownloadURL().matches("https?://[^/]+/[a-z0-9]+(/[^/]+)?\\.html")) {
                        link.setProperty("LINKUID", uid);
                        link.setUrlDownload(link.getDownloadURL().replaceAll("://[^/]+", "://turbobit.net"));
                    }
                    if (link.getDownloadURL().matches("https?://[^/]+/download/free/.*")) {
                        link.setProperty("LINKUID", uid);
                        link.setUrlDownload(MAINPAGE + "/" + uid + ".html");
                    }
                    if (link.getDownloadURL().matches("https?://[^/]+/?/download/redirect/.*")) {
                        link.setProperty("LINKUID", uid);
                        link.setUrlDownload(link.getDownloadURL().replaceAll("://[^/]+//download", "://turbobit.net/download"));
                    }
                } catch (PluginException e) {
                    return false;
                }
            }
        }
        try {
            final Browser br = new Browser();
            prepBrowser(br, null);
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br.setCookiesExclusive(true);
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            while (true) {
                links.clear();
                while (true) {
                    /* we test 50 links at once */
                    if (index == urls.length || links.size() > 49) {
                        break;
                    }
                    links.add(urls[index]);
                    index++;
                }
                sb.delete(0, sb.capacity());
                sb.append("links_to_check=");
                for (final DownloadLink dl : links) {
                    sb.append(Encoding.urlEncode("http://" + getHost() + "/" + dl.getProperty("LINKUID") + ".html"));
                    sb.append("%0A");
                }
                // remove last
                sb.delete(sb.length() - 3, sb.length());
                br.postPage("http://" + getHost() + "/linkchecker/check", sb.toString());
                for (final DownloadLink dllink : links) {
                    final Regex fileInfo = br.getRegex("<td>" + dllink.getProperty("LINKUID") + "</td>[\t\n\r ]+<td>([^<>/\"]+)</td>[\t\n\r ]+<td style=\"text-align:center;\"><img src=\"[^\"]+/(done|error)\\.png\"");
                    if (fileInfo.getMatches() == null || fileInfo.getMatches().length == 0) {
                        dllink.setAvailable(false);
                        logger.warning("Linkchecker broken for " + getHost() + " Example link: " + dllink.getDownloadURL());
                    } else {
                        if (fileInfo.getMatch(1).equals("error")) {
                            dllink.setAvailable(false);
                        } else {
                            final String name = fileInfo.getMatch(0);
                            dllink.setAvailable(true);
                            dllink.setFinalFileName(Encoding.htmlDecode(name.trim()));
                        }
                    }
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            return false;
        }
        return true;
    }

    private String escape(final String s) {
        /* CHECK: we should always use getBytes("UTF-8") or with wanted charset, never system charset! */
        final byte[] org = s.getBytes();
        final StringBuilder sb = new StringBuilder();
        String code;
        for (final byte element : org) {
            sb.append('%');
            code = Integer.toHexString(element);
            code = code.length() % 2 > 0 ? "0" + code : code;
            sb.append(code.substring(code.length() - 2));
        }
        return sb + "";
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            login(account, true);
        } catch (final PluginException e) {
            if (br.containsHTML("Our service is currently unavailable in your country\\.")) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nTurbobit.net is currently unavailable in your country!\r\nTurbobit.net ist in deinem Land momentan nicht verfügbar!", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
            throw e;
        }
        ai.setUnlimitedTraffic();
        String expire = br.getRegex("<u>(Turbo Access|Turbo Zugang)</u> to(.*?)(<a|</di)").getMatch(1);
        if (expire == null) {
            expire = br.getRegex("<u>Турбо доступ</u> до(.*?)(<a|</di)").getMatch(0);
            if (expire == null) {
                expire = br.getRegex("<img src=\\'/img/icon/yesturbo\\.png\\'> <u>.{5,20}</u> .{1,5} (.*?) <a href=\\'/turbo\\'>").getMatch(0);
            }
        }
        if (expire == null) {
            ai.setExpired(true);
            account.setValid(false);
            return ai;
        } else {
            ai.setValidUntil(TimeFormatter.getMilliSeconds(expire.trim(), "dd.MM.yyyy", Locale.ENGLISH));
        }
        ai.setStatus("Premium User");
        return ai;
    }

    @Override
    public String getAGBLink() {
        return "http://turbobit.net/rules";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    protected void showFreeDialog(final String domain) {
        if (System.getProperty("org.jdownloader.revision") != null) { /* JD2 ONLY! */
            super.showFreeDialog(domain);
            return;
        }
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run() {
                    try {
                        String lng = System.getProperty("user.language");
                        String message = null;
                        String title = null;
                        String tab = "                        ";
                        if ("de".equalsIgnoreCase(lng)) {
                            title = domain + " Free Download";
                            message = "Du lädst im kostenlosen Modus von " + domain + ".\r\n";
                            message += "Wie bei allen anderen Hostern holt JDownloader auch hier das Beste für dich heraus!\r\n\r\n";
                            message += tab + "  Falls du allerdings mehrere Dateien\r\n" + "          - und das möglichst mit Fullspeed und ohne Unterbrechungen - \r\n" + "             laden willst, solltest du dir den Premium Modus anschauen.\r\n\r\nUnserer Erfahrung nach lohnt sich das - Aber entscheide am besten selbst. Jetzt ausprobieren?  ";
                        } else {
                            title = domain + " Free Download";
                            message = "You are using the " + domain + " Free Mode.\r\n";
                            message += "JDownloader always tries to get the best out of each hoster's free mode!\r\n\r\n";
                            message += tab + "   However, if you want to download multiple files\r\n" + tab + "- possibly at fullspeed and without any wait times - \r\n" + tab + "you really should have a look at the Premium Mode.\r\n\r\nIn our experience, Premium is well worth the money. Decide for yourself, though. Let's give it a try?   ";
                        }
                        if (CrossSystem.isOpenBrowserSupported()) {
                            int result = JOptionPane.showConfirmDialog(jd.gui.swing.jdgui.JDGui.getInstance().getMainFrame(), message, title, JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null);
                            if (JOptionPane.OK_OPTION == result) {
                                CrossSystem.openURL(new URL("http://update3.jdownloader.org/jdserv/BuyPremiumInterface/redirect?" + domain + "&freedialog"));
                            }
                        }
                    } catch (Throwable e) {
                    }
                }
            });
        } catch (Throwable e) {
        }
    }

    private void checkShowFreeDialog() {
        SubConfiguration config = null;
        try {
            config = getPluginConfig();
            if (config.getBooleanProperty("premAdShown", Boolean.FALSE) == false) {
                if (config.getProperty("premAdShown2") == null) {
                    File checkFile = JDUtilities.getResourceFile("tmp/tbtmp");
                    if (!checkFile.exists()) {
                        checkFile.mkdirs();
                        showFreeDialog("turbobit.net");
                    }
                } else {
                    config = null;
                }
            } else {
                config = null;
            }
        } catch (final Throwable e) {
        } finally {
            if (config != null) {
                config.setProperty("premAdShown", Boolean.TRUE);
                config.setProperty("premAdShown2", "shown");
                config.save();
            }
        }
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        // support for public premium links
        if (downloadLink.getDownloadURL().matches("https?://[^/]+/download/redirect/.*")) {
            handlePremiumLink(downloadLink);
            return;
        }
        /*
         * we have to load the plugin first! we must not reference a plugin class without loading it before
         */
        JDUtilities.getPluginForDecrypt("linkcrypt.ws");
        requestFileInformation(downloadLink);
        checkShowFreeDialog();
        prepBrowser(br, userAgent.get());
        br.setCookie(MAINPAGE, "JD", "1");
        String dllink = downloadLink.getDownloadURL();
        sleep(2500, downloadLink);
        getPage(dllink);
        if (br.containsHTML("(>Please wait, searching file|\\'File not found\\. Probably it was deleted)")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String fileSize = br.getRegex("File size:</b>(.*?)</div>").getMatch(0);
        if (fileSize == null) {
            fileSize = br.getRegex("<span class=\\'file\\-icon.*?\\'>.*?</span>.*?\\((.*?)\\)").getMatch(0);
        }
        if (fileSize != null) {
            fileSize = fileSize.replace("М", "M");
            fileSize = fileSize.replace("к", "k");
            fileSize = fileSize.replace("Г", "g");
            fileSize = fileSize.replace("б", "");
            if (!fileSize.endsWith("b")) {
                fileSize = fileSize + "b";
            }
            downloadLink.setDownloadSize(SizeFormatter.getSize(fileSize.trim().replace(",", ".").replace(" ", "")));
        }
        String downloadUrl = null, waittime = null;
        String id = new Regex(dllink, "turbobit\\.net/(.*?)/.*?\\.html").getMatch(0);
        if (id == null) {
            id = new Regex(dllink, "turbobit\\.net/(.*?)\\.html").getMatch(0);
        }
        if (id == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        getPage("/download/free/" + id);

        Form captchaform = null;
        final Form[] allForms = br.getForms();
        if (allForms != null && allForms.length != 0) {
            for (final Form aForm : allForms) {
                if (aForm.containsHTML("captcha")) {
                    captchaform = aForm;
                    break;
                }
            }
        }

        if (captchaform == null) {
            if (br.containsHTML(tb(0))) {
                waittime = br.getRegex(tb(1)).getMatch(0);
                final int wait = waittime != null ? Integer.parseInt(waittime) : -1;

                if (wait > 31) {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, wait * 1001l);
                } else if (wait < 0) {
                } else {
                    sleep(wait * 1000l, downloadLink);
                }
            }
            waittime = br.getRegex(tb(1)).getMatch(0);
            if (waittime != null) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, Integer.parseInt(waittime) * 1001l);
            }
        }

        if (captchaform == null) {
            if (br.containsHTML("Our service is currently unavailable in your country\\.")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "Turbobit.net is currently unavailable in your country.");
            }
            logger.warning("captchaform equals null!");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (br.containsHTML(RECAPTCHATEXT)) {
            logger.info("Handling Re Captcha");
            final String theId = new Regex(br.toString(), "challenge\\?k=(.*?)\"").getMatch(0);
            if (theId == null) {
                logger.warning("the id for Re Captcha equals null!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
            final jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br);
            rc.setId(theId);
            rc.setForm(captchaform);
            rc.load();
            final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
            final String c = getCaptchaCode("recaptcha", cf, downloadLink);
            rc.getForm().setAction(MAINPAGE + "/download/free/" + id + "#");
            rc.setCode(c);
            if (br.containsHTML(RECAPTCHATEXT) || br.containsHTML("Incorrect, try again")) {
                try {
                    invalidateLastChallengeResponse();
                } catch (final Throwable e) {
                }
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
        } else {
            logger.info("Handling normal captchas");
            final String captchaUrl = br.getRegex(CAPTCHAREGEX).getMatch(0);
            if (captchaUrl == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            for (int i = 1; i <= 2; i++) {
                String captchaCode;
                if (!getPluginConfig().getBooleanProperty("JAC", false) || i == 2) {
                    captchaCode = getCaptchaCode("turbobit.net.disabled", captchaUrl, downloadLink);
                } else if (captchaUrl.contains("/basic/")) {
                    logger.info("Handling basic captchas");
                    captchaCode = getCaptchaCode("turbobit.net.basic", captchaUrl, downloadLink);
                } else {
                    captchaCode = getCaptchaCode(captchaUrl, downloadLink);
                }
                captchaform.put("captcha_response", captchaCode);
                br.submitForm(captchaform);
                if (br.getRegex(CAPTCHAREGEX).getMatch(0) == null) {
                    break;
                }
            }
            if (br.getRegex(CAPTCHAREGEX).getMatch(0) != null || br.containsHTML(RECAPTCHATEXT)) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
        }
        // Ticket Time
        String ttt = parseImageUrl(br.getRegex(jd.plugins.decrypter.LnkCrptWs.IMAGEREGEX(null)).getMatch(0), true);
        int maxWait = 9999, realWait = 0;
        for (String s : br.getRegex(tb(11)).getColumn(0)) {
            realWait = Integer.parseInt(s);
            if (realWait == 0) {
                continue;
            }
            if (realWait < maxWait) {
                maxWait = realWait;
            }
        }
        boolean waited = false;
        int tt = 60;
        if (ttt != null) {
            tt = Integer.parseInt(ttt);
            tt = tt < realWait ? tt : realWait;
            if (tt < 30 || tt > 600) {
                ttt = parseImageUrl(tb(2) + tt + "};" + br.getRegex(tb(3)).getMatch(0), false);
                if (ttt == null) {
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, BLOCKED, 10 * 60 * 60 * 1001l);
                }
                tt = Integer.parseInt(ttt);
            }
            logger.info(" Waittime detected, waiting " + String.valueOf(tt) + " seconds from now on...");
            if (tt > 250) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Limit reached or IP already loading", tt * 1001l);
            }
            waited = true;
        }
        final boolean use_js = false;

        if (use_js) {
            final Browser tOut = br.cloneBrowser();
            final String to = br.getRegex("(?i)(/\\w+/timeout\\.js\\?\\w+=[^\"\'<>]+)").getMatch(0);
            tOut.getPage(to == null ? "/files/timeout.js?ver=" + JDHash.getMD5(String.valueOf(Math.random())).toUpperCase(Locale.ENGLISH) : to);
            final String fun = escape(tOut.toString());
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");

            // realtime update
            String rtUpdate = getPluginConfig().getStringProperty("rtupdate", null);
            final boolean isUpdateNeeded = getPluginConfig().getBooleanProperty("isUpdateNeeded", false);
            int attemps = getPluginConfig().getIntegerProperty("attemps", 1);

            if (isUpdateNeeded || rtUpdate == null) {
                final Browser rt = new Browser();
                try {
                    rtUpdate = rt.getPage("http://update0.jdownloader.org/pluginstuff/tbupdate.js");
                    rtUpdate = JDHexUtils.toString(jd.plugins.decrypter.LnkCrptWs.IMAGEREGEX(rtUpdate.split("[\r\n]+")[1]));
                    getPluginConfig().setProperty("rtupdate", rtUpdate);
                } catch (Throwable e) {
                }
                getPluginConfig().setProperty("isUpdateNeeded", false);
                getPluginConfig().setProperty("attemps", attemps++);
                getPluginConfig().save();
            }

            String res = rhino("var id = \'" + id + "\';@" + fun + "@" + rtUpdate, 666);
            if (res == null || res != null && !res.matches(tb(10))) {
                res = rhino("var id = \'" + id + "\';@" + fun + "@" + rtUpdate, 100);
                if (new Regex(res, "/~ID~/").matches()) {
                    res = res.replaceAll("/~ID~/", id);
                }
            }

            if (res != null && res.matches(tb(10))) {
                sleep(tt * 1001, downloadLink);
                // Wed Jun 13 12:29:47 UTC 0200 2012
                SimpleDateFormat df = new SimpleDateFormat("EEE MMM dd HH:mm:ss zZ yyyy");
                Date date = new Date();
                br.setCookie(br.getHost(), "turbobit1", Encoding.urlEncode_light(df.format(date)).replace(":", "%3A"));

                br.getPage(res);
                downloadUrl = rhino(escape(br.toString()) + "@" + rtUpdate, 999);
                if (downloadUrl != null) {
                    downloadUrl = downloadUrl.replaceAll(MAINPAGE, "");
                    if (downloadUrl.equals("/download/free/" + id)) {
                        downloadUrl = null;
                    }
                }
                if (downloadUrl == null) {
                    downloadUrl = br.getRegex("(/download/redirect/[0-9A-F]{32}/" + dllink.replaceAll(MAINPAGE, "") + ")").getMatch(0);
                    if (downloadUrl == null) {
                        downloadUrl = br.getRegex("<a href=\'([^\']+)").getMatch(0);
                    }
                }
            }
            if (downloadUrl == null) {
                getPluginConfig().setProperty("isUpdateNeeded", true);
                if (br.containsHTML("The file is not avaliable now because of technical problems")) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, 15 * 60 * 1000l);
                }

                if (attemps > 1) {
                    getPluginConfig().setProperty("isUpdateNeeded", false);
                    getPluginConfig().setProperty("attemps", 1);
                    getPluginConfig().save();
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, BLOCKED, 10 * 60 * 60 * 1000l);
                } else {
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
            }
        } else {
            String continueLink = br.getRegex("\\$\\('#timeoutBox'\\)\\.load\\(\"(/[^\"]+)\"\\);").getMatch(0);
            if (continueLink == null) {
                continueLink = "/download/getLinkTimeout/" + id;
            }
            continueLink = "http://turbobit.net" + continueLink;
            if (!waited) {
                this.sleep(tt * 1001l, downloadLink);
            }
            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            br.getPage(continueLink);
            // Wed Jun 13 12:29:47 UTC 0200 2012
            SimpleDateFormat df = new SimpleDateFormat("EEE MMM dd HH:mm:ss zZ yyyy");
            Date date = new Date();
            br.setCookie(br.getHost(), "turbobit1", Encoding.urlEncode_light(df.format(date)).replace(":", "%3A"));
            downloadUrl = br.getRegex("(\"|')(/download/redirect/.*?)\\1").getMatch(1);
            if (downloadUrl == null) {
                if (br.toString().matches("Error: \\d+")) {
                    // unknown error...
                    throw new PluginException(LinkStatus.ERROR_RETRY);
                }
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        br.setFollowRedirects(false);
        // Future redirects at this point! We want to catch them and not process in order to get the MD5sum! example url structure
        // http://s\\d{2}.turbobit.ru:\\d+/download.php?name=FILENAME.FILEEXTENTION&md5=793379e72eef01ed1fa3fec91eff5394&fid=b5w4jikojflm&uid=free&speed=59&till=1356198536&trycount=1&ip=YOURIP&sid=60193f81464cca228e7bb240a0c39130&browser=201c88fd294e46f9424f724b0d1a11ff&did=800927001&sign=7c2e5d7b344b4a205c71c18c923f96ab
        br.getPage(downloadUrl);
        final String md5sum = new Regex(downloadUrl, "md5=([a-f0-9]{32})").getMatch(0);
        if (md5sum != null) {
            downloadLink.setMD5Hash(md5sum);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, downloadUrl, true, 1);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 404) {
                try {
                    dl.getConnection().disconnect();
                } catch (final Throwable e) {
                }
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            br.followConnection();
            if (br.containsHTML("Try to download it once again after")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 20 * 60 * 1000l);
            }
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, BLOCKED, 10 * 60 * 1000l);
        }
        handleServerErrors();
        dl.startDownload();
    }

    private void getPage(String dllink) throws Exception {
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getPage(dllink);
        antiDDoS();
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        // support for public premium links
        if (link.getDownloadURL().matches("https?://[^/]+/download/redirect/.*")) {
            handlePremiumLink(link);
            return;
        }
        requestFileInformation(link);
        login(account, false);
        sleep(2000, link);
        br.setCookie(MAINPAGE, "JD", "1");
        getPage(link.getDownloadURL());
        String dllink = null;
        final String[] mirrors = br.getRegex("(\\'|\")(http://([a-z0-9\\.]+)?turbobit\\.net//?download/redirect/.*?)(\\'|\")").getColumn(1);
        if (mirrors == null || mirrors.length == 0) {
            if (br.containsHTML("You have reached the.*? limit of premium downloads")) {
                logger.info("You have reached the.*? limit of premium downloads");
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
            if (br.containsHTML("'>Premium access is blocked<")) {
                logger.info("No traffic available");
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
            if (br.containsHTML("Our service is currently unavailable in your country\\.")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "Turbobit.net is currently unavailable in your country.");
            }
            logger.warning("dllink equals null, plugin seems to be broken!");
            if (br.getCookie("http://turbobit.net", "user_isloggedin") == null || "deleted".equalsIgnoreCase(br.getCookie("http://turbobit.net", "user_isloggedin"))) {
                synchronized (LOCK) {
                    account.setProperty("UA", null);
                    account.setProperty("cookies", null);
                }
                throw new PluginException(LinkStatus.ERROR_RETRY);
            }
            logger.info(NICE_HOST + ": unknown_dl_error_premium");
            int timesFailed = link.getIntegerProperty(NICE_HOSTproperty + "unknown_dl_error_premium", 0);
            link.getLinkStatus().setRetryCount(0);
            if (timesFailed <= 2) {
                timesFailed++;
                link.setProperty(NICE_HOSTproperty + "unknown_dl_error_premium", timesFailed);
                logger.info(NICE_HOST + ": unknown_dl_error_premium -> Retrying");
                throw new PluginException(LinkStatus.ERROR_RETRY, "unknown_dl_error_premium");
            } else {
                link.setProperty(NICE_HOSTproperty + "unknown_dl_error_premium", Property.NULL);
                logger.info(NICE_HOST + ": unknown_dl_error_premium - Plugin might be broken!");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        br.setFollowRedirects(false);
        boolean isdllable = false;
        for (final String currentlink : mirrors) {
            logger.info("Checking mirror: " + currentlink);
            br.getPage(currentlink);
            if (br.getRedirectLocation() != null) {
                dllink = br.getRedirectLocation();
                if (dllink != null) {
                    if (!dllink.contains("turbobit.net")) {
                        dllink = MAINPAGE + dllink;
                    }
                    isdllable = isDownloadable(dllink);
                    if (isdllable) {
                        logger.info("Mirror is okay: " + currentlink);
                        break;
                    } else {
                        logger.info("Mirror is down: " + currentlink);
                    }
                }
            }
        }
        if (!isdllable) {
            logger.info("Mirror: All mirrors failed -> Server error ");
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 30 * 60 * 1000l);
        }
        final String md5sum = new Regex(dllink, "md5=([a-f0-9]{32})").getMatch(0);
        if (md5sum != null) {
            link.setMD5Hash(md5sum);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 403) {
                try {
                    dl.getConnection().disconnect();
                } catch (final Throwable e) {
                }
                logger.info("No traffic available");
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
            if (dl.getConnection().getResponseCode() == 404) {
                try {
                    dl.getConnection().disconnect();
                } catch (final Throwable e) {
                }
                logger.info("File is offline");
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            br.followConnection();
            logger.warning("dllink doesn't seem to be a file...");
            handleGeneralServerErrors();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private boolean isDownloadable(final String directlink) {
        try {
            final Browser br2 = br.cloneBrowser();
            br2.setFollowRedirects(true);
            URLConnectionAdapter con = br2.openGetConnection(directlink);
            if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                return false;
            }
            con.disconnect();
        } catch (final Exception e) {
            return false;
        }
        return true;
    }

    public void handlePremiumLink(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        br.setCookie(MAINPAGE, "JD", "1");
        String dllink = link.getDownloadURL();
        br.setFollowRedirects(false);
        // Future redirects at this point, but we want to catch them not process
        // them in order to get the MD5sum. Which provided within the
        // URL args, within the final redirect
        // example url structure
        // http://s\\d{2}.turbobit.ru:\\d+/download.php?name=FILENAME.FILEEXTENTION&md5=793379e72eef01ed1fa3fec91eff5394&fid=b5w4jikojflm&uid=free&speed=59&till=1356198536&trycount=1&ip=YOURIP&sid=60193f81464cca228e7bb240a0c39130&browser=201c88fd294e46f9424f724b0d1a11ff&did=800927001&sign=7c2e5d7b344b4a205c71c18c923f96ab
        br.getPage(dllink);
        if (br.getRedirectLocation() != null) {
            dllink = br.getRedirectLocation();
        }
        if (dllink.matches(".+&md5=[a-z0-9]{32}.+")) {
            String md5sum = new Regex(dllink, "md5=([a-z0-9]{32})").getMatch(0);
            if (md5sum != null) {
                link.setMD5Hash(md5sum);
            }
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            if (dl.getConnection().getResponseCode() == 403) {
                try {
                    dl.getConnection().disconnect();
                } catch (final Throwable e) {
                }
                logger.info("No traffic available");
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
            if (dl.getConnection().getResponseCode() == 404) {
                try {
                    dl.getConnection().disconnect();
                } catch (final Throwable e) {
                }
                logger.info("File is offline");
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            br.followConnection();
            logger.warning("dllink doesn't seem to be a file...");
            if (br.containsHTML("Our service is currently unavailable in your country\\.")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "Turbobit.net is currently unavailable in your country.");
            }
            handleGeneralServerErrors();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        handleServerErrors();
        dl.startDownload();
    }

    private void handleGeneralServerErrors() throws PluginException {
        if (br.containsHTML("<h1>404 Not Found</h1>") || br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 30 * 60 * 1000l);
        }
        if (br.containsHTML("Try to download it once again after")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 'Try again later'", 20 * 60 * 1000l);
        }
        /* Either user waited too long for the captcha or maybe slow servers */
        if (br.containsHTML(">Ссылка просрочена\\. Пожалуйста получите")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 'link expired'", 5 * 60 * 1000l);
        }
    }

    private void handleServerErrors() throws PluginException {
        if (dl.getConnection().getLongContentLength() == 0) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error, server sends empty file", 5 * 60 * 1000l);
        }
    }

    // do not add @Override here to keep 0.* compatibility
    public boolean hasAutoCaptcha() {
        return true;
    }

    // do not add @Override here to keep 0.* compatibility
    public boolean hasCaptcha() {
        return true;
    }

    private static AtomicReference<String> userAgent = new AtomicReference<String>(null);

    /**
     * Defines custom browser requirements. Integrates with antiDDoS method
     *
     * @author raztoki
     *
     * */
    private Browser prepBrowser(final Browser prepBr, String UA) {
        synchronized (antiDDoSCookies) {
            if (!antiDDoSCookies.isEmpty()) {
                for (final Map.Entry<String, String> cookieEntry : antiDDoSCookies.entrySet()) {
                    final String key = cookieEntry.getKey();
                    final String value = cookieEntry.getValue();
                    prepBr.setCookie(this.getHost(), key, value);
                }
            }
        }
        if (UA == null) {
            /* we first have to load the plugin, before we can reference it */
            JDUtilities.getPluginForHost("mediafire.com");
            userAgent.set(jd.plugins.hoster.MediafireCom.stringUserAgent());
            UA = userAgent.get();
        }
        prepBr.getHeaders().put("Pragma", null);
        prepBr.getHeaders().put("Cache-Control", null);
        prepBr.getHeaders().put("Accept-Charset", null);
        prepBr.getHeaders().put("Accept", "text/html, application/xhtml+xml, */*");
        prepBr.getHeaders().put("Accept-Language", "en-EN");
        prepBr.getHeaders().put("User-Agent", UA);
        prepBr.getHeaders().put("Referer", null);
        prepBr.setCustomCharset("UTF-8");

        // required for antiDDoS support, without the need to repeat requests.
        try {
            /* not available in old stable */
            prepBr.setAllowedResponseCodes(new int[] { 503 });
        } catch (Throwable e) {
        }
        return prepBr;
    }

    private static HashMap<String, String> antiDDoSCookies = new HashMap<String, String>();

    /**
     * Performs Cloudflare and Incapsula requirements.<br />
     * Auto fill out the required fields and updates antiDDoSCookies session.<br />
     * Always called after Browser Request!
     *
     * @version 0.02
     * @author raztoki
     **/
    private void antiDDoS() throws Exception {
        if (br == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final HashMap<String, String> cookies = new HashMap<String, String>();
        if (br.getHttpConnection() != null) {
            final String URL = br.getURL();
            if (requestHeadersHasKeyNValueContains("server", "cloudflare-nginx")) {
                Form cloudflare = br.getFormbyProperty("id", "ChallengeForm");
                if (cloudflare == null) {
                    cloudflare = br.getFormbyProperty("id", "challenge-form");
                }
                if (br.getHttpConnection().getResponseCode() == 403 && cloudflare != null) {
                    // new method seems to be within 403
                    if (cloudflare.hasInputFieldByName("recaptcha_response_field")) {
                        // they seem to add multiple input fields which is most likely meant to be corrected by js ?
                        // we will manually remove all those
                        while (cloudflare.hasInputFieldByName("recaptcha_response_field")) {
                            cloudflare.remove("recaptcha_response_field");
                        }
                        while (cloudflare.hasInputFieldByName("recaptcha_challenge_field")) {
                            cloudflare.remove("recaptcha_challenge_field");
                        }
                        // this one is null, needs to be ""
                        if (cloudflare.hasInputFieldByName("message")) {
                            cloudflare.remove("message");
                            cloudflare.put("messsage", "\"\"");
                        }
                        // recaptcha bullshit
                        String apiKey = cloudflare.getRegex("/recaptcha/api/(?:challenge|noscript)\\?k=([A-Za-z0-9%_\\+\\- ]+)").getMatch(0);
                        if (apiKey == null) {
                            apiKey = br.getRegex("/recaptcha/api/(?:challenge|noscript)\\?k=([A-Za-z0-9%_\\+\\- ]+)").getMatch(0);
                            if (apiKey == null) {
                                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                            }
                        }
                        final DownloadLink dllink = new DownloadLink(null, "antiDDoS Provider 'Clouldflare' requires Captcha", MAINPAGE, MAINPAGE, true);
                        final PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
                        final jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br);
                        rc.setId(apiKey);
                        rc.load();
                        final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                        final String response = getCaptchaCode(cf, dllink);
                        cloudflare.put("recaptcha_challenge_field", rc.getChallenge());
                        cloudflare.put("recaptcha_response_field", Encoding.urlEncode(response));
                        br.submitForm(cloudflare);
                        if (br.getFormbyProperty("id", "ChallengeForm") != null || br.getFormbyProperty("id", "challenge-form") != null) {
                            logger.warning("Possible plugin error within cloudflare handling");
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                    }
                } else if (br.getHttpConnection().getResponseCode() == 503 && cloudflare != null) {
                    // 503 response code with javascript math section
                    String host = new Regex(URL, "https?://([^/]+)(:\\d+)?/").getMatch(0);
                    String math = br.getRegex("\\$\\('#jschl_answer'\\)\\.val\\(([^\\)]+)\\);").getMatch(0);
                    if (math == null) {
                        math = br.getRegex("a\\.value = ([\\d\\-\\.\\+\\*/]+);").getMatch(0);
                    }
                    if (math == null) {
                        String variableName = br.getRegex("(\\w+)\\s*=\\s*\\$\\('#jschl_answer'\\);").getMatch(0);
                        if (variableName != null) {
                            variableName = variableName.trim();
                        }
                        math = br.getRegex(variableName + "\\.val\\(([^\\)]+)\\)").getMatch(0);
                    }
                    if (math == null) {
                        logger.warning("Couldn't find 'math'");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    // use js for now, but change to Javaluator as the provided string doesn't get evaluated by JS according to Javaluator
                    // author.
                    ScriptEngineManager mgr = jd.plugins.hoster.DummyScriptEnginePlugin.getScriptEngineManager(this);
                    ScriptEngine engine = mgr.getEngineByName("JavaScript");
                    final long value = ((Number) engine.eval("(" + math + ") + " + host.length())).longValue();
                    cloudflare.put("jschl_answer", value + "");
                    Thread.sleep(5500);
                    br.submitForm(cloudflare);
                    if (br.getFormbyProperty("id", "ChallengeForm") != null || br.getFormbyProperty("id", "challenge-form") != null) {
                        logger.warning("Possible plugin error within cloudflare handling");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                } else {
                    // nothing wrong, or something wrong (unsupported format)....
                    // commenting out return prevents caching of cookies per request
                    // return;
                }
                // get cookies we want/need.
                // refresh these with every getPage/postPage/submitForm?
                final Cookies add = br.getCookies(this.getHost());
                for (final Cookie c : add.getCookies()) {
                    if (new Regex(c.getKey(), "(cfduid|cf_clearance)").matches()) {
                        cookies.put(c.getKey(), c.getValue());
                    }
                }
            }
            // save the session!
            synchronized (antiDDoSCookies) {
                antiDDoSCookies.clear();
                antiDDoSCookies.putAll(cookies);
            }
        }
    }

    /**
     *
     * @author raztoki
     * */
    private boolean requestHeadersHasKeyNValueStartsWith(final String k, final String v) {
        if (k == null || v == null) {
            return false;
        }
        for (HTTPHeader s : br.getRequest().getHeaders()) {
            if (s.getKey().startsWith(k) && s.getValue().startsWith(v)) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @author raztoki
     * */
    private boolean requestHeadersHasKeyNValueContains(final String k, final String v) {
        if (k == null || v == null) {
            return false;
        }
        for (HTTPHeader s : br.getRequest().getHeaders()) {
            if (s.getKey().contains(k) && s.getValue().contains(v)) {
                return true;
            }
        }
        return false;
    }

    private boolean isJava7nJDStable() {
        if (System.getProperty("jd.revision.jdownloaderrevision") == null && System.getProperty("java.version").matches("1\\.[7-9].+")) {
            return true;
        } else {
            return false;
        }
    }

    private void login(final Account account, final boolean force) throws Exception {
        synchronized (LOCK) {
            // Load cookies
            try {
                setBrowserExclusive();
                String ua = null;
                if (force == false) {
                    /*
                     * we have to reuse old UA, else the cookie will become invalid
                     */
                    ua = account.getStringProperty("UA", null);
                }
                prepBrowser(br, ua);
                br.setCookie(MAINPAGE, "set_user_lang_change", "en");
                br.setFollowRedirects(true);
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
                // lets set a new agent
                prepBrowser(br, null);
                getPage(MAINPAGE);
                br.postPage(MAINPAGE + "/user/login", "user%5Blogin%5D=" + Encoding.urlEncode(account.getUser()) + "&user%5Bpass%5D=" + Encoding.urlEncode(account.getPass()) + "&user%5Bmemory%5D=on&user%5Bsubmit%5D=Login");
                // Check for stupid login captcha
                if (br.containsHTML(">Limit of login attempts exceeded") || br.containsHTML(">Please enter the captcha")) {
                    logger.info("processing login captcha...");
                    final DownloadLink dummyLink = new DownloadLink(this, "Account", "turbobit.net", "http://turbobit.net", true);
                    final String captchaLink = br.getRegex("\"(http://turbobit\\.net/captcha/[^<>\"]*?)\"").getMatch(0);
                    if (captchaLink != null) {
                        final String code = getCaptchaCode("NOTOLDTRBT", captchaLink, dummyLink);
                        String captchaSubtype = "3";
                        if (captchaLink.contains("/basic/")) {
                            captchaSubtype = "5";
                        }
                        br.postPage(MAINPAGE + "/user/login", "user%5Blogin%5D=" + Encoding.urlEncode(account.getUser()) + "&user%5Bpass%5D=" + Encoding.urlEncode(account.getPass()) + "&user%5Bcaptcha_response%5D=" + Encoding.urlEncode(code) + "&user%5Bcaptcha_type%5D=securimg&user%5Bcaptcha_subtype%5D=" + captchaSubtype + "&user%5Bsubmit%5D=Login");
                    } else {
                        final PluginForHost recplug = JDUtilities.getPluginForHost("DirectHTTP");
                        final jd.plugins.hoster.DirectHTTP.Recaptcha rc = ((DirectHTTP) recplug).getReCaptcha(br);
                        final String id = br.getRegex("\\?k=([A-Za-z0-9%_\\+\\- ]+)\"").getMatch(0);
                        if (id == null) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nLogin failed, please contact our support!\r\nLogin fehlgeschlagen, bitte kontaktiere unseren Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                        rc.setId(id);
                        rc.load();
                        final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                        final String c = getCaptchaCode("RECAPTCHA", cf, dummyLink);
                        br.postPage(MAINPAGE + "/user/login", "user%5Blogin%5D=" + Encoding.urlEncode(account.getUser()) + "&user%5Bpass%5D=" + Encoding.urlEncode(account.getPass()) + "&recaptcha_challenge_field=" + rc.getChallenge() + "&recaptcha_response_field=" + Encoding.urlEncode(c) + "&user%5Bcaptcha_type%5D=recaptcha&user%5Bcaptcha_subtype%5D=&user%5Bmemory%5D=on&user%5Bsubmit%5D=Login");
                    }
                }
                // valid premium (currently no traffic)|valid premium (traffic available)
                if (!br.containsHTML("/banturbo\\.png\\'>|icon/yesturbo\\.png\\'>")) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid accounttype (no premium account)!\r\nUngültiger Accounttyp (kein Premiumaccount)!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                if (br.getCookie(MAINPAGE + "/", "sid") == null) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nUngültiger Benutzername oder ungültiges Passwort!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                // cookies
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = br.getCookies(br.getHost());
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
                account.setProperty("UA", userAgent.get());
            } catch (final PluginException e) {
                account.setProperty("UA", Property.NULL);
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    private String parseImageUrl(String fun, final boolean NULL) {
        if (fun == null) {
            return null;
        }
        if (!NULL) {
            final String[] next = fun.split(tb(9));
            if (next == null || next.length != 2) {
                fun = rhino(fun, 0);
                if (fun == null) {
                    return null;
                }
                fun = new Regex(fun, tb(4)).getMatch(0);
                return fun == null ? new Regex(fun, tb(5)).getMatch(0) : rhino(fun, 2);
            }
            return rhino(next[1], 1);
        }
        return new Regex(fun, tb(1)).getMatch(0);
    }

    // Also check HitFileNet plugin if this one is broken
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        /** Old linkcheck code can be found in rev 16195 */
        checkLinks(new DownloadLink[] { downloadLink });
        if (!downloadLink.isAvailabilityStatusChecked()) {
            return AvailableStatus.UNCHECKED;
        }
        if (downloadLink.isAvailabilityStatusChecked() && !downloadLink.isAvailable()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    private String rhino(final String s, final int b) {
        Object result = new Object();
        final ScriptEngineManager manager = jd.plugins.hoster.DummyScriptEnginePlugin.getScriptEngineManager(this);
        final ScriptEngine engine = manager.getEngineByName("javascript");
        try {
            switch (b) {
            case 0:
                engine.eval(s + tb(6));
                result = engine.get(tb(7));
                break;
            case 1:
                result = ((Double) engine.eval(tb(8))).longValue();
                break;
            case 2:
                engine.eval("var out=\"" + s + "\";");
                result = engine.get("out");
                break;
            case 100:
                String[] code = s.split("@");
                engine.eval(code[0] + "var b = 3;var inn = \'" + code[1] + "\';" + code[2]);
                result = engine.get("out");
                break;
            case 666:
                code = s.split("@");
                engine.eval(code[0] + "var b = 1;var inn = \'" + code[1] + "\';" + code[2]);
                result = engine.get("out");
                break;
            case 999:
                code = s.split("@");
                engine.eval("var b = 2;var inn = \'" + code[0] + "\';" + code[1]);
                result = engine.get("out");
                break;
            }
        } catch (final Throwable e) {
            return null;
        }
        return result != null ? result.toString() : null;
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "JAC", JDL.L("plugins.hoster.turbobit.jac", "Enable JAC?")).setDefaultValue(true));
    }

    private String tb(final int i) {
        final String[] s = new String[12];
        s[0] = "fe8cfbfafa57cde31bc2b798df5141ab2dc171ec0852d89a1a135e3f116c83d15d8bf93a";
        s[1] = "fddbfbfafa57cdef1a90b5cedf5647ae2cc572ec0958dd981e125c68156882d65d82f869";
        s[2] = "fdd9fbf2fb05cde71a97b69edf5742f1289470bb0a5bd9c81a1b5e39116c85805982fc6e880ce26a201651b8ea211874e4232d90c59b6462ac28d2b26f0537385fa6";
        s[3] = "f980f8f7fa0acdb21b91b6cbdf5043fc2ac775ea080fd8c71a4f5d68156586d05982fd3e8b5ae33f244555e8eb201d77e12128cbc1c7";
        s[4] = "f980ffa5fa07cdb01a93b6c8de0642ae299571bb0c0ddb9c1a1b5b6f143d84855ddfff6b8b5de66e254553eeea751d72e17e2d98c19a6760af75d6b46b05";
        s[5] = "f980ffa5f951ceb31ec7b3c8da5246fa2ac770bc0b0fdc9c1e13";
        s[6] = "fc8efbf2fb01c9e61bc2b798df5146f82cc075bf0b5fd8c71a4e5f3e153a8781588ff86f890de26a221050eaee701824e4742d9cc1c66238a973";
        s[7] = "fddefaf6fb07";
        s[8] = "fe8cfbfafa57cde31bc2b798df5146ad29c071b6080edbca1a135f6f156984d75982fc6e8800e338";
        s[9] = "ff88";
        s[10] = "f9def8a1fa02c9b21ac5b5c9da0746ae2ac671be0c0fd99f194e5b69113a85d65c8bf86e8d00e23d254751eded741d72e7262ecdc19c6267af72d2e26b5e326a59a5ce295d28f89e21ae29ea523acfb545fd8adb";
        s[11] = "f980fea5fa0ac9ef1bc7b694de0142f1289075bd0d0ddb9d1b195a6d103d82865cddff69890ae76a251b53efef711d74e07e299bc098";
        /*
         * we have to load the plugin first! we must not reference a plugin class without loading it before
         */
        JDUtilities.getPluginForDecrypt("linkcrypt.ws");
        return JDHexUtils.toString(jd.plugins.decrypter.LnkCrptWs.IMAGEREGEX(s[i]));
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null) {
            /* no account, yes we can expect captcha */
            return true;
        }
        if (Boolean.TRUE.equals(acc.getBooleanProperty("free"))) {
            /* free accounts also have captchas */
            return true;
        }
        return false;
    }
}