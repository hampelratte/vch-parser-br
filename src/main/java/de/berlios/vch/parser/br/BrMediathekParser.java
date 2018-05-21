package de.berlios.vch.parser.br;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.log.LogService;

import de.berlios.vch.http.client.HttpUtils;
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

    public static final String BASE_URI = "https://www.br.de";
    public static final String GRAPH_URI = "https://proxy-base.master.mango.express/graphql";
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String CHARSET = "UTF-8";

    private static final SortedMap<String, String[]> aBiszParams = new TreeMap<String, String[]>();
    static {
        aBiszParams.put("A", new String[] {"startsWith", "A"});
        aBiszParams.put("B", new String[] {"startsWith", "B"});
        aBiszParams.put("C", new String[] {"startsWith", "C"});
        aBiszParams.put("D", new String[] {"startsWith", "D"});
        aBiszParams.put("E", new String[] {"startsWith", "E"});
        aBiszParams.put("F", new String[] {"startsWith", "F"});
        aBiszParams.put("G", new String[] {"startsWith", "G"});
        aBiszParams.put("H", new String[] {"startsWith", "H"});
        aBiszParams.put("I", new String[] {"startsWith", "I"});
        aBiszParams.put("J", new String[] {"startsWith", "J"});
        aBiszParams.put("K", new String[] {"startsWith", "K"});
        aBiszParams.put("L", new String[] {"startsWith", "L"});
        aBiszParams.put("M", new String[] {"startsWith", "M"});
        aBiszParams.put("N", new String[] {"startsWith", "N"});
        aBiszParams.put("O", new String[] {"startsWith", "O"});
        aBiszParams.put("P", new String[] {"startsWith", "P"});
        aBiszParams.put("Q", new String[] {"startsWith", "Q"});
        aBiszParams.put("R", new String[] {"startsWith", "R"});
        aBiszParams.put("S", new String[] {"startsWith", "S"});
        aBiszParams.put("T", new String[] {"startsWith", "T"});
        aBiszParams.put("U", new String[] {"startsWith", "U"});
        aBiszParams.put("V", new String[] {"startsWith", "V"});
        aBiszParams.put("W", new String[] {"startsWith", "W"});
        aBiszParams.put("X", new String[] {"startsWith", "X"});
        aBiszParams.put("Y", new String[] {"startsWith", "Y"});
        aBiszParams.put("Z", new String[] {"startsWith", "Z"});
        aBiszParams.put("0-9", new String[] {"matches", "^[^a-zA-Z].*$"});
    }

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

        for (Entry<String, String[]> entry : aBiszParams.entrySet()) {
            String letter = entry.getKey();
            OverviewPage page = new OverviewPage();
            page.setParser(ID);
            page.setTitle(letter);
            page.setUri(new URI("br://letter/" + letter));
            root.getPages().add(page);
        }
        return root;
    }

    private OverviewPage createRootPage() throws URISyntaxException {
        OverviewPage page = new OverviewPage();
        page.setParser(ID);
        page.setTitle(getTitle());
        page.setUri(new URI("vchpage://localhost/" + getId()));
        return page;
    }

    @Override
    public IWebPage parse(IWebPage page) throws Exception {
        if (page instanceof IOverviewPage) {
            if(page.getUri().getHost().equals("letter")) {
                String letter = page.getUri().getPath().substring(1);
                parseAlphabetPage((IOverviewPage) page, letter);
            } else if(page.getUri().getHost().equals("program")) {
                IOverviewPage opage = (IOverviewPage) page;
                parseProgramPage(opage);
            }
        } else {
            IVideoPage video = parseVideoPage(page);
            return video;
        }
        return page;
    }

    private void parseAlphabetPage(IOverviewPage page, String letter) throws Exception {
        JSONObject request = new JSONObject(aBiszRequest);
        JSONObject variables = request.getJSONObject("variables");
        JSONObject seriesFilter = variables.getJSONObject("seriesFilter");
        JSONObject title = seriesFilter.getJSONObject("title");
        title.remove("startsWith");
        String[] query = aBiszParams.get(letter);
        String predicate = query[0];
        String value = query[1];
        title.put(predicate, value);

        Map<String, String> header = HttpUtils.createFirefoxHeader();
        header.put("Referer", "https://www.br.de/mediathek/sendungen_a-z");
        header.put("origin", "https://www.br.de");
        header.put("DNT", "1");
        header.put("content-type", "application/json");
        String response = HttpUtils.post(GRAPH_URI, header, request.toString().getBytes("utf-8"), "UTF-8");
        JSONObject resp = new JSONObject(response);
        JSONObject data = resp.getJSONObject("data");
        JSONObject viewer = data.getJSONObject("viewer");
        JSONObject seriesIndexAllSeries = viewer.getJSONObject("seriesIndexAllSeries");
        JSONArray edges = seriesIndexAllSeries.getJSONArray("edges");
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.getJSONObject(i);
            JSONObject node = edge.getJSONObject("node");
            String programTitle = node.getString("title");
            String id = node.getString("id");

            OverviewPage progPage = new OverviewPage();
            progPage.setParser(getId());
            progPage.setTitle(programTitle);
            progPage.setUri(new URI("br://program/" + id));
            page.getPages().add(progPage);
        }
    }

    private void parseProgramPage(IOverviewPage page) throws Exception {
        String id = page.getUri().getPath().substring(1);
        JSONObject request = new JSONObject(programOverviewRequest);
        JSONObject variables = request.getJSONObject("variables");
        variables.put("id", id);

        Map<String, String> header = HttpUtils.createFirefoxHeader();
        header.put("Referer", "https://www.br.de/mediathek/sendungen_a-z");
        header.put("origin", "https://www.br.de");
        header.put("DNT", "1");
        header.put("content-type", "application/json");
        String response = HttpUtils.post(GRAPH_URI, header, request.toString().getBytes("utf-8"), "UTF-8");
        JSONObject resp = new JSONObject(response);
        JSONObject data = resp.getJSONObject("data");
        JSONObject viewer = data.getJSONObject("viewer");
        JSONObject series = viewer.getJSONObject("series");
        JSONObject previousEpisodes = series.getJSONObject("previousEpisodes");
        JSONArray edges = previousEpisodes.getJSONArray("edges");
        for (int i = 0; i < edges.length(); i++) {
            JSONObject edge = edges.getJSONObject(i);
            JSONObject node = edge.getJSONObject("node");
            VideoPage video = new VideoPage();
            video.setParser(getId());
            video.setTitle(node.getString("title"));
            video.setDuration(node.getLong("duration"));
            video.setPublishDate(parsePubDate(node));
            video.setThumbnail(parseThumbnail(node));
            video.setUri(new URI("br://video/" + node.getString("id")));
            video.setVideoUri(new URI("https://www.hampelratte.org"));
            page.getPages().add(video);
        }
    }

    private URI parseThumbnail(JSONObject node) {
        try {
            JSONObject defaultTeaserImage = node.getJSONObject("defaultTeaserImage");
            JSONObject imageFiles = defaultTeaserImage.getJSONObject("imageFiles");
            JSONArray edges = imageFiles.getJSONArray("edges");
            JSONObject n = edges.getJSONObject(0).getJSONObject("node");
            return new URI(n.getString("publicLocation"));
        } catch (Exception e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse thumbnail", e);
            return null;
        }
    }

    private Calendar parsePubDate(JSONObject node) {
        Calendar pubDate = Calendar.getInstance();
        try {
            // 2018-04-22T23:30:00.000Z
            JSONObject broadcasts = node.getJSONObject("broadcasts");
            JSONArray edges = broadcasts.getJSONArray("edges");
            JSONObject n = edges.getJSONObject(0).getJSONObject("node");
            String start = n.getString("start");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            Date broadcastDate = sdf.parse(start);
            pubDate.setTimeInMillis(broadcastDate.getTime());
        } catch (Exception e) {
            logger.log(LogService.LOG_WARNING, "Couldn't parse publish date", e);
            pubDate.setTimeInMillis(0);
        }
        return pubDate;
    }

    private IVideoPage parseVideoPage(IWebPage page) throws JSONException, UnsupportedEncodingException, IOException, URISyntaxException {
        IVideoPage video = (IVideoPage) page;
        String id = page.getUri().getPath().substring(1);
        JSONObject request = new JSONObject(videoRequest);
        JSONObject variables = request.getJSONObject("variables");
        variables.put("clipId", id);

        Map<String, String> header = HttpUtils.createFirefoxHeader();
        header.put("Referer", "https://www.br.de/mediathek/sendungen_a-z");
        header.put("origin", "https://www.br.de");
        header.put("DNT", "1");
        header.put("content-type", "application/json");
        String response = HttpUtils.post(GRAPH_URI, header, request.toString().getBytes("utf-8"), "UTF-8");
        JSONObject resp = new JSONObject(response);
        JSONObject data = resp.getJSONObject("data");
        JSONObject viewer = data.getJSONObject("viewer");
        JSONObject detailClip = viewer.getJSONObject("detailClip");
        String description = detailClip.getString("shortDescription");
        video.setDescription(description);
        JSONObject clip = viewer.getJSONObject("clip");
        video.setVideoUri(parseVideoUri(clip));
        return video;
    }

    private URI parseVideoUri(JSONObject clip) throws JSONException, URISyntaxException {
        JSONObject videoFiles = clip.getJSONObject("videoFiles");
        JSONArray edges = videoFiles.getJSONArray("edges");
        URI bestUri = null;
        int bestHeight = 0;
        for (int i = 0; i < edges.length(); i++) {
            JSONObject node = edges.getJSONObject(i).getJSONObject("node");
            if(node.has("videoProfile")) {
                JSONObject profile = node.getJSONObject("videoProfile");
                Object h = profile.get("height");
                if(h instanceof Integer) {
                    int height = ((Integer) h).intValue();
                    if(height > bestHeight) {
                        bestHeight = height;
                        bestUri = new URI(node.getString("publicLocation"));
                    }
                }
            }
        }
        return bestUri;
    }

    private static final String aBiszRequest = "{\n" +
            "  \"variables\": {\"seriesFilter\": {\n" +
            "    \"audioOnly\": {\"eq\": false},\n" +
            "    \"title\": {\"startsWith\": \"A\"},\n" +
            "    \"status\": {\"id\": {\"eq\": \"av:http://ard.de/ontologies/lifeCycle#published\"}}\n" +
            "  }},\n" +
            "  \"query\": \"query SeriesIndexPageQuery(\\n  $seriesFilter: SeriesFilter!\\n) {\\n  viewer {\\n    ...SeriesIndex_viewer_19SNIy\\n    id\\n  }\\n}\\n\\nfragment SeriesIndex_viewer_19SNIy on Viewer {\\n  seriesIndexAllSeries: allSeries(first: 1000, orderBy: TITLE_ASC, filter: $seriesFilter) {\\n    edges {\\n      node {\\n        __typename\\n        id\\n        title\\n        ...SeriesTeaserBox_node\\n        ...TeaserListItem_node\\n      }\\n    }\\n  }\\n}\\n\\nfragment SeriesTeaserBox_node on Node {\\n  __typename\\n  id\\n  ... on CreativeWorkInterface {\\n    ...TeaserImage_creativeWorkInterface\\n  }\\n  ... on SeriesInterface {\\n    ...SubscribeAction_series\\n    subscribed\\n    title\\n    ...LinkWithSlug_creativeWork\\n  }\\n}\\n\\nfragment TeaserListItem_node on Node {\\n  __typename\\n  id\\n  ... on SeriesInterface {\\n    ...LinkWithSlug_creativeWork\\n  }\\n  ... on CreativeWorkInterface {\\n    ...TeaserImage_creativeWorkInterface\\n  }\\n  ... on ClipInterface {\\n    title\\n  }\\n}\\n\\nfragment LinkWithSlug_creativeWork on CreativeWorkInterface {\\n  id\\n  slug\\n}\\n\\nfragment TeaserImage_creativeWorkInterface on CreativeWorkInterface {\\n  id\\n  teaserImages(first: 1) {\\n    edges {\\n      node {\\n        __typename\\n        shortDescription\\n        copyright\\n        id\\n      }\\n    }\\n  }\\n  defaultTeaserImage {\\n    __typename\\n    shortDescription\\n    copyright\\n    imageFiles(first: 1) {\\n      edges {\\n        node {\\n          __typename\\n          id\\n          publicLocation\\n          crops(first: 10, filter: {format: ASPECT_RATIO_16_9}) {\\n            count\\n            edges {\\n              node {\\n                __typename\\n                publicLocation\\n                width\\n                height\\n                id\\n              }\\n            }\\n          }\\n        }\\n      }\\n    }\\n    id\\n  }\\n}\\n\\nfragment SubscribeAction_series on SeriesInterface {\\n  id\\n  subscribed\\n}\\n\"\n" +
            "}";

    private static final String programOverviewRequest = "{\n" +
            "  \"variables\": {\n" +
            "    \"previousEpisodesFilter\": {\n" +
            "      \"essences\": {\"empty\": {\"eq\": false}},\n" +
            "      \"status\": {\"id\": {\"eq\": \"av:http://ard.de/ontologies/lifeCycle#published\"}}\n" +
            "    },\n" +
            "    \"clipCount\": 25,\n" +
            "    \"latestEpisodeFilter\": {\"essences\": {\"empty\": {\"eq\": false}}},\n" +
            "    \"id\": \"av:584f4ca83b467900117c3afb\"\n" +
            "  },\n" +
            "  \"query\": \"query SeriesPageRendererQuery(\\n  $id: ID!\\n  $clipCount: Int\\n  $previousEpisodesFilter: ProgrammeFilter\\n  $latestEpisodeFilter: ProgrammeFilter\\n) {\\n  viewer {\\n    ...SeriesPage_viewer_2sm1bc\\n    id\\n  }\\n}\\n\\nfragment SeriesPage_viewer_2sm1bc on Viewer {\\n  series(id: $id) {\\n    __typename\\n    id\\n    slug\\n    title\\n    shortDescription\\n    ...TeaserImage_creativeWorkInterface\\n    ...SeriesBrandBanner_series\\n    ...ChildContentRedirect_creativeWork\\n    ...HomoDigitalisWidget_series\\n    latestEpisode: episodes(orderBy: VERSIONFROM_DESC, first: 1, filter: $latestEpisodeFilter) {\\n      edges {\\n        node {\\n          __typename\\n          title\\n          kicker\\n          items(first: 30, filter: {essences: {empty: {eq: false}}, status: {id: {eq: \\\"av:http://ard.de/ontologies/lifeCycle#published\\\"}}}) {\\n            count\\n            ...ItemSlider_items\\n          }\\n          id\\n        }\\n      }\\n    }\\n    previousEpisodes: episodes(first: $clipCount, orderBy: VERSIONFROM_DESC, filter: $previousEpisodesFilter) {\\n      count\\n      pageInfo {\\n        hasNextPage\\n      }\\n      edges {\\n        node {\\n          __typename\\n          ...SmallTeaserBox_node\\n          id\\n        }\\n      }\\n    }\\n  }\\n}\\n\\nfragment TeaserImage_creativeWorkInterface on CreativeWorkInterface {\\n  id\\n  teaserImages(first: 1) {\\n    edges {\\n      node {\\n        __typename\\n        shortDescription\\n        copyright\\n        id\\n      }\\n    }\\n  }\\n  defaultTeaserImage {\\n    __typename\\n    shortDescription\\n    copyright\\n    imageFiles(first: 1) {\\n      edges {\\n        node {\\n          __typename\\n          id\\n          publicLocation\\n          crops(first: 10, filter: {format: ASPECT_RATIO_16_9}) {\\n            count\\n            edges {\\n              node {\\n                __typename\\n                publicLocation\\n                width\\n                height\\n                id\\n              }\\n            }\\n          }\\n        }\\n      }\\n    }\\n    id\\n  }\\n}\\n\\nfragment SeriesBrandBanner_series on SeriesInterface {\\n  ...SubscribeAction_series\\n  title\\n  shortDescription\\n  externalURLS(first: 1) {\\n    edges {\\n      node {\\n        __typename\\n        id\\n        url\\n        label\\n      }\\n    }\\n  }\\n  brandingImages(first: 1) {\\n    edges {\\n      node {\\n        __typename\\n        imageFiles(first: 1) {\\n          edges {\\n            node {\\n              __typename\\n              publicLocation\\n              id\\n            }\\n          }\\n        }\\n        id\\n      }\\n    }\\n  }\\n}\\n\\nfragment ChildContentRedirect_creativeWork on CreativeWorkInterface {\\n  categories(first: 100) {\\n    edges {\\n      node {\\n        __typename\\n        id\\n      }\\n    }\\n  }\\n}\\n\\nfragment HomoDigitalisWidget_series on SeriesInterface {\\n  title\\n}\\n\\nfragment ItemSlider_items on ItemConnection {\\n  edges {\\n    node {\\n      __typename\\n      ...SmallTeaserBox_node\\n      id\\n    }\\n  }\\n}\\n\\nfragment SmallTeaserBox_node on Node {\\n  id\\n  ... on CreativeWorkInterface {\\n    ...TeaserImage_creativeWorkInterface\\n  }\\n  ... on ClipInterface {\\n    id\\n    title\\n    kicker\\n    ...LinkWithSlug_creativeWork\\n    ...Bookmark_clip\\n    ...Duration_clip\\n    ...Progress_clip\\n  }\\n  ... on ProgrammeInterface {\\n    broadcasts(first: 1, orderBy: START_DESC) {\\n      edges {\\n        node {\\n          __typename\\n          start\\n          id\\n        }\\n      }\\n    }\\n  }\\n}\\n\\nfragment LinkWithSlug_creativeWork on CreativeWorkInterface {\\n  id\\n  slug\\n}\\n\\nfragment Bookmark_clip on ClipInterface {\\n  id\\n  bookmarked\\n  title\\n}\\n\\nfragment Duration_clip on ClipInterface {\\n  duration\\n}\\n\\nfragment Progress_clip on ClipInterface {\\n  myInteractions {\\n    __typename\\n    progress\\n    completed\\n    id\\n  }\\n}\\n\\nfragment SubscribeAction_series on SeriesInterface {\\n  id\\n  subscribed\\n}\\n\"\n" +
            "}";

    private static final String videoRequest = "{\"query\":\"query DetailPageRendererQuery(\\n  $clipId: ID!\\n  $isClip: Boolean!\\n  $isLivestream: Boolean!\\n  $livestream: ID!\\n) {\\n  viewer {\\n    ...DetailPage_viewer_22r5xP\\n    id\\n  }\\n}\\n\\nfragment DetailPage_viewer_22r5xP on Viewer {\\n  ...VideoPlayer_viewer_22r5xP\\n  ...ClipActions_viewer\\n  detailClip: clip(id: $clipId) {\\n    __typename\\n    id\\n    title\\n    kicker\\n    slug\\n    shortDescription\\n    ...ClipActions_clip\\n    ...ClipInfo_clip\\n    ...ChildContentRedirect_creativeWork\\n  }\\n}\\n\\nfragment VideoPlayer_viewer_22r5xP on Viewer {\\n  id\\n  clip(id: $clipId) @include(if: $isClip) {\\n    __typename\\n    id\\n    title\\n    ageRestriction\\n    videoFiles(first: 100) {\\n      edges {\\n        node {\\n          __typename\\n          id\\n          mimetype\\n          publicLocation\\n          videoProfile {\\n            __typename\\n            id\\n            width\\n          }\\n        }\\n      }\\n    }\\n    ...Track_clip\\n    ...Error_clip\\n    ...Settings_clip\\n    ...Finished_clip\\n    defaultTeaserImage @include(if: $isClip) {\\n      __typename\\n      imageFiles(first: 1) {\\n        edges {\\n          node {\\n            __typename\\n            id\\n            publicLocation\\n          }\\n        }\\n      }\\n      id\\n    }\\n    myInteractions {\\n      __typename\\n      completed\\n      progress\\n      id\\n    }\\n  }\\n  livestream(id: $livestream) @include(if: $isLivestream) {\\n    __typename\\n    id\\n    streamingUrls(first: 10, filter: {hasEmbeddedSubtitles: {eq: false}}) {\\n      edges {\\n        node {\\n          __typename\\n          id\\n          publicLocation\\n          hasEmbeddedSubtitles\\n        }\\n      }\\n    }\\n  }\\n}\\n\\nfragment ClipActions_viewer on Viewer {\\n  me {\\n    __typename\\n    bookmarks(first: 12) {\\n      ...BookmarkAction_bookmarks\\n    }\\n    id\\n  }\\n}\\n\\nfragment ClipActions_clip on ClipInterface {\\n  id\\n  bookmarked\\n  downloadable\\n  ...BookmarkAction_clip\\n  ...Rate_clip\\n  ...Share_clip\\n  ...Download_clip\\n}\\n\\nfragment ClipInfo_clip on ClipInterface {\\n  __typename\\n  id\\n  title\\n  kicker\\n  description\\n  shortDescription\\n  availableUntil\\n  ...Subtitles_clip\\n  ...Duration_clip\\n  ...FSKInfo_clip\\n  ... on ProgrammeInterface {\\n    publications(first: 1) {\\n      edges {\\n        node {\\n          __typename\\n          publishedBy {\\n            __typename\\n            name\\n            id\\n          }\\n          id\\n        }\\n      }\\n    }\\n    broadcasts(first: 1) {\\n      edges {\\n        node {\\n          __typename\\n          start\\n          id\\n        }\\n      }\\n    }\\n    episodeOf {\\n      __typename\\n      description\\n      id\\n      title\\n      scheduleInfo\\n      subscribed\\n      ...SubscribeAction_series\\n      ... on CreativeWorkInterface {\\n        ...LinkWithSlug_creativeWork\\n        ...TeaserImage_creativeWorkInterface\\n      }\\n    }\\n  }\\n  ... on ItemInterface {\\n    itemOf(first: 1) {\\n      edges {\\n        node {\\n          __typename\\n          publications(first: 1) {\\n            edges {\\n              node {\\n                __typename\\n                publishedBy {\\n                  __typename\\n                  name\\n                  id\\n                }\\n                id\\n              }\\n            }\\n          }\\n          broadcasts(first: 1) {\\n            edges {\\n              node {\\n                __typename\\n                start\\n                id\\n              }\\n            }\\n          }\\n          episodeOf {\\n            __typename\\n            id\\n            title\\n            scheduleInfo\\n            subscribed\\n            ...SubscribeAction_series\\n            ... on CreativeWorkInterface {\\n              ...LinkWithSlug_creativeWork\\n              ...TeaserImage_creativeWorkInterface\\n            }\\n          }\\n          id\\n        }\\n      }\\n    }\\n  }\\n}\\n\\nfragment ChildContentRedirect_creativeWork on CreativeWorkInterface {\\n  categories(first: 100) {\\n    edges {\\n      node {\\n        __typename\\n        id\\n      }\\n    }\\n  }\\n}\\n\\nfragment Subtitles_clip on ClipInterface {\\n  videoFiles(first: 100) {\\n    edges {\\n      node {\\n        __typename\\n        subtitles {\\n          edges {\\n            node {\\n              __typename\\n              timedTextFiles(filter: {mimetype: {eq: \\\"text/vtt\\\"}}) {\\n                edges {\\n                  node {\\n                    __typename\\n                    publicLocation\\n                    id\\n                  }\\n                }\\n              }\\n              id\\n            }\\n          }\\n        }\\n        id\\n      }\\n    }\\n  }\\n}\\n\\nfragment Duration_clip on ClipInterface {\\n  duration\\n}\\n\\nfragment FSKInfo_clip on ClipInterface {\\n  ageRestriction\\n}\\n\\nfragment SubscribeAction_series on SeriesInterface {\\n  id\\n  subscribed\\n}\\n\\nfragment LinkWithSlug_creativeWork on CreativeWorkInterface {\\n  id\\n  slug\\n}\\n\\nfragment TeaserImage_creativeWorkInterface on CreativeWorkInterface {\\n  id\\n  teaserImages(first: 1) {\\n    edges {\\n      node {\\n        __typename\\n        shortDescription\\n        copyright\\n        id\\n      }\\n    }\\n  }\\n  defaultTeaserImage {\\n    __typename\\n    shortDescription\\n    copyright\\n    imageFiles(first: 1) {\\n      edges {\\n        node {\\n          __typename\\n          id\\n          publicLocation\\n          crops(first: 10, filter: {format: ASPECT_RATIO_16_9}) {\\n            count\\n            edges {\\n              node {\\n                __typename\\n                publicLocation\\n                width\\n                height\\n                id\\n              }\\n            }\\n          }\\n        }\\n      }\\n    }\\n    id\\n  }\\n}\\n\\nfragment BookmarkAction_clip on ClipInterface {\\n  id\\n}\\n\\nfragment Rate_clip on ClipInterface {\\n  id\\n  reactions {\\n    likes\\n    dislikes\\n  }\\n  myInteractions {\\n    __typename\\n    reaction {\\n      __typename\\n      id\\n    }\\n    id\\n  }\\n}\\n\\nfragment Share_clip on ClipInterface {\\n  title\\n  id\\n  embeddable\\n  slug\\n}\\n\\nfragment Download_clip on ClipInterface {\\n  videoFiles(first: 100) {\\n    edges {\\n      node {\\n        __typename\\n        publicLocation\\n        videoProfile {\\n          __typename\\n          height\\n          id\\n        }\\n        id\\n      }\\n    }\\n  }\\n}\\n\\nfragment BookmarkAction_bookmarks on ClipRemoteConnection {\\n  count\\n  ...TeaserSlider_clipRemoteConnection\\n}\\n\\nfragment TeaserSlider_clipRemoteConnection on ClipRemoteConnection {\\n  edges {\\n    node {\\n      __typename\\n      ...SmallTeaserBox_node\\n      id\\n    }\\n  }\\n}\\n\\nfragment SmallTeaserBox_node on Node {\\n  id\\n  ... on CreativeWorkInterface {\\n    ...TeaserImage_creativeWorkInterface\\n  }\\n  ... on ClipInterface {\\n    id\\n    title\\n    kicker\\n    ...LinkWithSlug_creativeWork\\n    ...Bookmark_clip\\n    ...Duration_clip\\n    ...Progress_clip\\n  }\\n  ... on ProgrammeInterface {\\n    broadcasts(first: 1, orderBy: START_DESC) {\\n      edges {\\n        node {\\n          __typename\\n          start\\n          id\\n        }\\n      }\\n    }\\n  }\\n}\\n\\nfragment Bookmark_clip on ClipInterface {\\n  id\\n  bookmarked\\n  title\\n}\\n\\nfragment Progress_clip on ClipInterface {\\n  myInteractions {\\n    __typename\\n    progress\\n    completed\\n    id\\n  }\\n}\\n\\nfragment Track_clip on ClipInterface {\\n  videoFiles(first: 100) {\\n    edges {\\n      node {\\n        __typename\\n        publicLocation\\n        subtitles {\\n          edges {\\n            node {\\n              id\\n              language\\n              closed\\n              __typename\\n              timedTextFiles(filter: {mimetype: {eq: \\\"text/vtt\\\"}}) {\\n                edges {\\n                  node {\\n                    __typename\\n                    id\\n                    mimetype\\n                    publicLocation\\n                  }\\n                }\\n              }\\n            }\\n          }\\n        }\\n        id\\n      }\\n    }\\n  }\\n}\\n\\nfragment Error_clip on ClipInterface {\\n  ageRestriction\\n}\\n\\nfragment Settings_clip on ClipInterface {\\n  videoFiles(first: 100) {\\n    edges {\\n      node {\\n        __typename\\n        id\\n        mimetype\\n        publicLocation\\n        videoProfile {\\n          __typename\\n          id\\n          width\\n          height\\n        }\\n      }\\n    }\\n  }\\n}\\n\\nfragment Finished_clip on ClipInterface {\\n  recommendations(first: 3, filter: {audioOnly: {eq: false}, withEssences: {eq: true}}) {\\n    recommendationId\\n    edges {\\n      node {\\n        __typename\\n        ...FinishedSuggestedItem_clip\\n        id\\n      }\\n    }\\n  }\\n}\\n\\nfragment FinishedSuggestedItem_clip on ClipInterface {\\n  id\\n  title\\n  duration\\n  ...TeaserImage_creativeWorkInterface\\n}\\n\",\"variables\":{\"clipId\":\"av:5a96a98144ea9900178bd207\",\"isClip\":true,\"isLivestream\":false,\"livestream\":\"Livestream:\"}}";
}