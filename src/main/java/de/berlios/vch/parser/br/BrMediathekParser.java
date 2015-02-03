package de.berlios.vch.parser.br;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.osgi.service.log.LogService;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.HtmlParserUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.IWebParser;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;

@Component
@Provides
public class BrMediathekParser implements IWebParser {

    public static final String ID = BrMediathekParser.class.getName();

    public static final String BASE_URI = "http://www.br.de";
    public static final String ROOT_URI = BASE_URI + "/mediathek/video/sendungen/index.html";
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String CHARSET = "UTF-8";

    @Requires
    private LogService logger;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return "BR Mediathek";
    }

    @Override
    public IOverviewPage getRoot() throws Exception {
        OverviewPage root = createRootPage();

        // get the root page
        String content = HttpUtils.get(ROOT_URI, null, CHARSET);
        parseRootPage(root, content);

        return root;
    }

    private OverviewPage createRootPage() throws URISyntaxException {
        OverviewPage page = new OverviewPage();
        page.setParser(ID);
        page.setTitle(getTitle());
        page.setUri(new URI("vchpage://localhost/" + getId()));
        return page;
    }

    private void parseRootPage(OverviewPage root, String content) throws Exception {
        Elements categories = HtmlParserUtils.getTags(content, "div.broadcastList");
        for (Iterator<Element> iterator = categories.iterator(); iterator.hasNext();) {
            Element cat = iterator.next();
            String character = cat.attr("id");

            // create one page for each letter
            IOverviewPage opage = createCategoryPage(root, character);
            root.getPages().add(opage);

            // parse the programs of the letter
            parsePrograms(opage, cat);
        }
    }

    private IOverviewPage createCategoryPage(OverviewPage root, String character) throws URISyntaxException {
        OverviewPage opage = new OverviewPage();
        opage.setParser(ID);
        opage.setTitle(character);
        opage.setUri(new URI(BASE_URI + "#letter=" + character));
        return opage;
    }

    private void parsePrograms(IOverviewPage opage, Element cat) throws Exception {
        String divContent = cat.html();
        Elements listItems = HtmlParserUtils.getTags(divContent, "ul.clearFix li");
        for (Iterator<Element> iterator = listItems.iterator(); iterator.hasNext();) {
            Element li = iterator.next();
            String liContent = li.html();
            Element a = HtmlParserUtils.getTag(liContent, "a");
            // ImageTag img = (ImageTag) HtmlParserUtils.getTag(liContent, CHARSET, "a image");
            Elements spans = HtmlParserUtils.getTags(liContent, "a > span");
            String title = spans.first().text();

            OverviewPage progPage = new OverviewPage();
            progPage.setParser(getId());
            progPage.setTitle(title);
            progPage.setUri(new URI(BASE_URI + a.attr("href")));
            opage.getPages().add(progPage);
        }
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        if (page instanceof IOverviewPage) {
            IOverviewPage opage = (IOverviewPage) page;
            if (!opage.getUri().toString().contains("#letter")) {
                parseProgramPage(opage);
            }
        } else {
            IVideoPage video = parseVideoPage(page);
            return video;
        }
        return page;
    }

    private IVideoPage parseVideoPage(IWebPage page) throws IOException, SAXException, ParserConfigurationException, ParseException, URISyntaxException {
        String htmlPage = page.getUri().toString();
        String content = HttpUtils.get(htmlPage, null, CHARSET);

        logger.log(LogService.LOG_DEBUG, "Loading program page " + page.getUri());
        Pattern p = Pattern.compile("\\.setup\\(\\{dataURL:'(.*)'}");
        Matcher m = p.matcher(content);
        if (m.find()) {
            String playerConfigUri = BASE_URI + m.group(1);
            logger.log(LogService.LOG_DEBUG, "Found player config at " + playerConfigUri);
            IVideoPage video = parsePlayerConfig(playerConfigUri);
            logger.log(LogService.LOG_DEBUG, "Video " + video.getVideoUri().toString());
            return video;
        } else {
            logger.log(LogService.LOG_ERROR, "Player config not found ");
            throw new RuntimeException("Player config not found");
        }
    }

    private void parseProgramPage(IOverviewPage page) throws Exception {
        // parse the first "main" video
        try {
            IVideoPage firstVideo = parseVideoPage(page);
            page.getPages().add(firstVideo);
        } catch (Exception e) {
            // some pages don't have a video on the program page
            logger.log(LogService.LOG_WARNING, "No video found on the program page");
        }

        // parse the other videos
        String htmlPage = page.getUri().toString();
        String content = HttpUtils.get(htmlPage, null, CHARSET);
        Elements teaserLinks = HtmlParserUtils.getTags(content, "section#teaserBundleSeries article a");
        if (teaserLinks.size() == 0) {
            teaserLinks = HtmlParserUtils.getTags(content, "section#teaserBundleHome article a");
        }
        for (Element link : teaserLinks) {
            VideoPage video = new VideoPage();
            video.setParser(getId());
            video.setUri(new URI(BASE_URI + link.attr("href")));
            String name = link.getElementsByClass("name").first().text();
            String episode = link.getElementsByClass("episode").first().text();
            video.setTitle(name + " - " + episode);
            page.getPages().add(video);
        }
    }

    private IVideoPage parsePlayerConfig(String playerConfigUri) throws IOException, SAXException, ParserConfigurationException, ParseException,
            URISyntaxException {

        String content = HttpUtils.get(playerConfigUri, null, CHARSET);
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(content)));
        Node video = doc.getElementsByTagName("video").item(0);

        VideoPage vpage = parseAssets(doc);
        vpage.setParser(getId());

        // parse the title
        String title = findChildWithTagName(video, "title").getTextContent();
        vpage.setTitle(title);

        // parse the description
        String desc = findChildWithTagName(video, "desc").getTextContent();
        vpage.setDescription(desc);

        // parse the video page uri
        String permalink = findChildWithTagName(video, "permalink").getTextContent();
        vpage.setUri(new URI(permalink));

        // parse the publish date
        Calendar pubDate = parsePubDate(video);
        vpage.setPublishDate(pubDate);

        // parse the duration
        vpage.setDuration(parseDuration(video));

        // parse the thumbnail
        vpage.setThumbnail(parseThumbnail(doc));

        return vpage;
    }

    private URI parseThumbnail(Document playerConfig) {
        org.w3c.dom.NodeList thumbnails = playerConfig.getElementsByTagName("variant");

        int bestWidth = 0;
        URI bestUrl = null;
        for (int i = 0; i < thumbnails.getLength(); i++) {
            Node thumbnail = thumbnails.item(i);
            try {
                int width = Integer.parseInt(findChildWithTagName(thumbnail, "width").getTextContent());
                if (width > bestWidth) {
                    bestWidth = width;
                    bestUrl = new URI(BASE_URI + findChildWithTagName(thumbnail, "url").getTextContent());
                }
            } catch (Exception e) {
                logger.log(LogService.LOG_WARNING, "Error parsing thumbnail", e);
                continue;
            }
        }
        return bestUrl;
    }

    private long parseDuration(Node video) {
        long duration = 0;
        try {
            String durationString = findChildWithTagName(video, "duration").getTextContent();
            Pattern p = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})");
            Matcher m = p.matcher(durationString);
            if (m.matches()) {
                int hours = Integer.parseInt(m.group(1));
                duration += TimeUnit.HOURS.toSeconds(hours);

                int minutes = Integer.parseInt(m.group(2));
                duration += TimeUnit.MINUTES.toSeconds(minutes);

                int seconds = Integer.parseInt(m.group(3));
                duration += seconds;
            }
        } catch (Exception e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse duration", e);
        }

        return duration;
    }

    private VideoPage parseAssets(Document playerConfig) throws ParserConfigurationException, URISyntaxException {
        VideoPage vpage = new VideoPage();
        org.w3c.dom.NodeList assets = playerConfig.getElementsByTagName("asset");

        int bestWidth = 0;
        String bestUrl = "";
        for (int i = 0; i < assets.getLength(); i++) {
            Node asset = assets.item(i);
            try {
                int width = Integer.parseInt(findChildWithTagName(asset, "frameWidth").getTextContent());
                if (width > bestWidth) {
                    bestWidth = width;
                    bestUrl = findChildWithTagName(asset, "downloadUrl").getTextContent();
                }
            } catch (DOMException e) {
                // ignore this asset
                logger.log(LogService.LOG_WARNING, "Ignoring asset without frameWidth");
                continue;
            }
        }
        vpage.setVideoUri(new URI(bestUrl));
        return vpage;
    }

    private Calendar parsePubDate(Node video) {
        Calendar pubDate = null;
        try {
            String dateString = findChildWithTagName(video, "broadcastDate").getTextContent();
            Date broadcastDate = new SimpleDateFormat("dd.MM.yyyy").parse(dateString);
            pubDate = GregorianCalendar.getInstance();
            pubDate.setTimeInMillis(broadcastDate.getTime());
        } catch (Exception e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse publish date", e);
            pubDate = Calendar.getInstance();
            pubDate.setTimeInMillis(0);
        }
        return pubDate;
    }

    private Node findChildWithTagName(Node parent, String tagName) throws DOMException, ParserConfigurationException {
        if (parent == null) {
            return null;
        }

        // logger.log(LogService.LOG_DEBUG, "------------");
        // logger.log(LogService.LOG_DEBUG, parent.getNodeName() + " <=> " + tagName);

        org.w3c.dom.NodeList childs = parent.getChildNodes();
        // logger.log(LogService.LOG_DEBUG, "# children " + childs.getLength());
        for (int i = 0; i < childs.getLength(); i++) {
            Node child = childs.item(i);
            // logger.log(LogService.LOG_DEBUG, child.getNodeName() + " <=> " + tagName);
            if (child.getNodeName().equals(tagName)) {
                return child;
            } else if (child.hasChildNodes()) {
                // logger.log(LogService.LOG_DEBUG, "Checking children of " + child.getNodeName());
                // Node result = findChildWithTagName(child, tagName);
                // if (result != null) {
                // logger.log(LogService.LOG_DEBUG, "Result " + result.getNodeName());
                // return result;
                // } else {
                // logger.log(LogService.LOG_DEBUG, "Not found in children. Continueing with next node");
                // }
            }
        }

        throw new DOMException((short) -1, "Child " + tagName + " not found for " + parent.getNodeName());
    }
}