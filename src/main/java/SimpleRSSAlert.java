import com.github.kevinsawicki.http.HttpRequest;
import com.mrpowergamerbr.temmiewebhook.DiscordEmbed;
import com.mrpowergamerbr.temmiewebhook.DiscordMessage;
import com.mrpowergamerbr.temmiewebhook.Response;
import com.mrpowergamerbr.temmiewebhook.TemmieWebhook;
import com.mrpowergamerbr.temmiewebhook.embed.FieldEmbed;
import com.mrpowergamerbr.temmiewebhook.embed.FooterEmbed;
import com.mrpowergamerbr.temmiewebhook.embed.ThumbnailEmbed;
import com.mrpowergamerbr.temmiewebhook.exceptions.WebhookException;
import net.dean.jraw.RedditClient;
import net.dean.jraw.http.NetworkAdapter;
import net.dean.jraw.http.OkHttpNetworkAdapter;
import net.dean.jraw.http.UserAgent;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Subreddit;
import net.dean.jraw.models.SubredditSort;
import net.dean.jraw.oauth.Credentials;
import net.dean.jraw.oauth.OAuthHelper;
import net.dean.jraw.pagination.DefaultPaginator;
import net.dean.jraw.references.SubredditReference;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.yaml.snakeyaml.Yaml;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SimpleRSSAlert {
    private Integer intervalInHrs;
    private ArrayList<String> rssFeeds;
    private String discordWebHook;
    private String slackWebHook;
    private JSONObject options;
    private HashMap<String, ArrayList<String>> lastUpdatedRSSFeed; //Website -> Posts
    private String optionsPath;
    private RedditClient redditClient;
    private TemmieWebhook temmie;


    public SimpleRSSAlert() {
        intervalInHrs = 0;
        rssFeeds = new ArrayList<>();
        discordWebHook = "";
        slackWebHook = "";
        options = new JSONObject();
        lastUpdatedRSSFeed = new HashMap<>();
        optionsPath = "src/main/resources/options.json";
        redditClient = null;
        temmie = null;
    }

    public static void main(String[] args) {
        SimpleRSSAlert sra = new SimpleRSSAlert();
        sra.setup();
        sra.redditSetup();

        //Get latest RSS Feed content, if it differs from previous, send information to discord/slack
        while (true) {
            sra.getUpdatesFromRSSFeeds();
            wait(60000 * 5);
        }
    }

    private static void wait(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void setup() {
        JSONParser jsonParser = new JSONParser();
        JSONObject options = null;
        String resourcesPath = "src/main/resources/";
        String optionsFile = "options.json";

        try (FileReader reader = new FileReader(resourcesPath + optionsFile)) {
            JSONObject optionObj = (JSONObject) jsonParser.parse(reader);
            options = optionObj;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        //Create folder for storing latest RSS Feed
        String folderName = "rssFeedHistory";
        File file = new File(resourcesPath + folderName);
        Path pathToDir = Paths.get(resourcesPath + folderName);
        if (!Files.exists(pathToDir)) {
            boolean bool = file.mkdir();
            if (bool) {
                System.out.println("RSS Feed Histroy directory created successfully at: " + file.getAbsolutePath());
            } else {
                System.out.println("Setup Failed At Stage: 'RSS Feed History Directory Creation");
            }
        }
        this.options = options;
        this.intervalInHrs = (Integer) options.get("Interval");
        this.rssFeeds = (ArrayList<String>) options.get("rssfeeds");
        this.discordWebHook = (String) options.get("discordWebHook");
        this.slackWebHook = (String) options.get("slackWebHook");
        this.temmie = new TemmieWebhook(this.discordWebHook);
    }

    private void redditSetup() {
        String username = null, password = null, app_id = null, app_secret = null;
        Yaml yaml = new Yaml();
        File yamlFile = new File("src/main/redditUsernamePassword.yaml");
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(yamlFile);
            Map<String, Object> obj = yaml.load(inputStream);
            username = (String) obj.get("username");
            password = (String) obj.get("password");
            app_id = (String) obj.get("app-id");
            app_secret = (String) obj.get("app-secret");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        UserAgent userAgent = new UserAgent("bot", "SimpleRSSAlert", "v0.1", username);
        // Create our credentials
        Credentials credentials = Credentials.script(username, password, app_id, app_secret);

        // This is what really sends HTTP requests
        NetworkAdapter adapter = new OkHttpNetworkAdapter(userAgent);

        // Authenticate and get a RedditClient instance
        redditClient = OAuthHelper.automatic(adapter, credentials);
    }

    private void getUpdatesFromRSSFeeds() {
        if (this.rssFeeds.size() == 0) {
            System.out.println("No RSS Feeds in " + this.optionsPath);
        } else {
            for (String rssFeed : this.rssFeeds) {
                if (lastUpdatedRSSFeed.containsKey(rssFeed) == false) {
                    ArrayList<String> posts = new ArrayList<>();
                    lastUpdatedRSSFeed.put(rssFeed, posts);
                }

                if (rssFeed.contains("reddit")) {
                    String subredditName = this.convertRedditURLtoSubreddit(rssFeed);
                    this.handleGetFromReddit(rssFeed, subredditName);
                } else {
                    this.handleGetGeneral(rssFeed);
                }


            }
        }
    }

    private void handleGetFromReddit(String rssFeed, String subreddit) {
        String latestInfo = this.getLatestInfo(subreddit);
        boolean newInfo = false;
        ArrayList<String> curList = lastUpdatedRSSFeed.get(rssFeed);
        SubredditReference sr = redditClient.subreddit(subreddit);

        DefaultPaginator.Builder<Submission, SubredditSort> paginatorBuilder = sr.posts();
        DefaultPaginator<Submission> paginator = paginatorBuilder.limit(5).sorting(SubredditSort.NEW).build();

        Listing<Submission> curPage = paginator.next();

        for (Submission post : curPage) {
            System.out.println(post.getTitle() + ": " + post.getUrl());

            //If we already have this post, we have reached latest
            //Break out of for loop.
            if ((latestInfo != null && latestInfo.equals(post.getTitle())) || curList.contains(post.getTitle())) {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();
                System.out.println(dtf.format(now) + " we are at latest post, size of array: " + curList.size());
                break;
            } else {
                curList.add(post.getTitle());
                this.postToWebhookDiscord(post);
                newInfo = true;
            }
        }
        if (curList.size() != 0 && newInfo == true) {
            this.saveToHistory(subreddit, curList);
        }

    }

    /*
        Will handle non-reddit feeds, should be in rss format.
     */
    private void handleGetGeneral(String website) {

    }

    private String getLatestInfo(String key) {
        String fileName = key;
        String rssFeedHistoryPath = "src/main/resources/rssFeedHistory/";
        File file = new File(rssFeedHistoryPath + fileName + ".txt");

        String result = null;
        try {
            Scanner myReader = new Scanner(file);
            while (myReader.hasNextLine()) {
                result = myReader.nextLine();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return result;
    }

    private void saveToHistory(String key, ArrayList<String> lastUpdatedInformation) {
        String fileName = key;
        String rssFeedHistoryPath = "src/main/resources/rssFeedHistory/";
        File file = new File(rssFeedHistoryPath + fileName + ".txt");

        try {
            FileWriter fw = new FileWriter(file);
            //Latest post should be first item in array.
            fw.write(lastUpdatedInformation.get(0));
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
        Assumes all reddit links are of the form 'https://reddit.com/r/buildapcsales/'
     */
    private String convertRedditURLtoSubreddit(String redditURL) {
        int indexOfR = redditURL.indexOf("/r/");
        System.out.println(indexOfR);
        String subreddit = redditURL.substring(indexOfR + 3, redditURL.length() - 1);
        return subreddit;
    }

    private void postToWebhookDiscord(Submission redditPost) {
        DiscordEmbed embeded = generateEmbededInfo(redditPost);
        String imageURL = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/46/Generic_Feed-icon.svg/256px-Generic_Feed-icon.svg.png";

        DiscordMessage dm = DiscordMessage.builder()
                .username("RSS-BOT")
                .content("**" + redditPost.getTitle() + "**")
                .avatarUrl(imageURL)
                .embeds(Arrays.asList(embeded))
                .build();
        temmie.sendMessage(dm);
    }

    private static DiscordEmbed generateEmbededInfo(Submission redditPost) {
        DiscordEmbed de = null;

        //If default/no thumbnail, we send a message without thumbnailembed, otherwise we would get an error.
        if (redditPost.getThumbnail().equals("default")) {
            de = DiscordEmbed.builder()
                    .title(redditPost.getTitle())
                    .description("[**Comments**](https://reddit.com" + redditPost.getPermalink() + ")\n" + redditPost.getDomain())
                    .url(redditPost.getUrl())
                    .footer(FooterEmbed.builder()
                            .text(redditPost.getCreated().toString())
                            .build())
                    .fields(Arrays.asList(
                            FieldEmbed.builder()
                                    .name("Author")
                                    .value(redditPost.getAuthor())
                                    .build(),
                            FieldEmbed.builder()
                                    .name("Comment Count")
                                    .value(redditPost.getCommentCount().toString())
                                    .build(),
                            FieldEmbed.builder()
                                    .name("Vote Count")
                                    .value(String.valueOf(redditPost.getScore()))
                                    .build()))
                    .build();
        } else {
            de = DiscordEmbed.builder()
                    .title(redditPost.getTitle())
                    .description("[**Comments**](https://reddit.com" + redditPost.getPermalink() + ")\n" + redditPost.getDomain())
                    .url(redditPost.getUrl())
                    .footer(FooterEmbed.builder()
                            .text(redditPost.getCreated().toString())
                            .build())
                    .thumbnail(ThumbnailEmbed.builder()
                            .url(redditPost.getThumbnail())
                            .height(128)
                            .build())
                    .fields(Arrays.asList(
                            FieldEmbed.builder()
                                    .name("Author")
                                    .value(redditPost.getAuthor())
                                    .build(),
                            FieldEmbed.builder()
                                    .name("Comment Count")
                                    .value(redditPost.getCommentCount().toString())
                                    .build(),
                            FieldEmbed.builder()
                                    .name("Vote Count")
                                    .value(String.valueOf(redditPost.getScore()))
                                    .build()))
                    .build();
        }
        return de;
    }
}

