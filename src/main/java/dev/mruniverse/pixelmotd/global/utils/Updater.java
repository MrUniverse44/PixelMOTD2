package dev.mruniverse.pixelmotd.global.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mruniverse.pixelmotd.global.GLogger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;


@SuppressWarnings("deprecation")
public class Updater {
    private static final String USER_AGENT = "Updater by Stipess1";
    // Direct download link
    private String downloadLink;
    // Provided plugin
    private final GLogger logs;
    // The folder where update will be downloaded
    private final File updateFolder;
    // The plugin file
    private final File file;
    // ID of a project
    private final int id;
    // return a page
    private int page = 1;
    // Set the update type
    private final UpdateType updateType;
    // Get the outcome result
    private Result result = Result.SUCCESS;
    // If next page is empty set it to true, and get info from previous page.
    private boolean emptyPage;
    // Version returned from spigot
    private String version;
    // Version of the plugin
    private final String currentVersion;
    // If true updater is going to log progress to the console.
    private boolean logger = true;
    // Updater thread
    private final Thread thread;

    private static final String DOWNLOAD = "/download";
    private static final String VERSIONS = "/versions";
    private static final String PAGE = "?page=";
    private static final String API_RESOURCE = "https://api.spiget.org/v2/resources/";

    public Updater(GLogger logger,String currentVersion, int id, File file, UpdateType updateType)
    {
        this.logs = logger;
        this.currentVersion = currentVersion;
        this.updateFolder = new File(file,"downloads");
        if(!updateFolder.exists()) {
            if (updateFolder.mkdirs()) logger.info("Downloads folder has been created");
        }
        this.id = id;
        this.file = file;
        this.updateType = updateType;

        downloadLink = API_RESOURCE + id;

        thread = new Thread(new UpdaterRunnable());
        thread.start();
    }

    @SuppressWarnings("unused")
    public void disableLogs() {
        logger = false;
    }

    public enum UpdateType
    {
        // Checks only the version
        VERSION_CHECK,
        // Downloads without checking the version
        DOWNLOAD,
        // If updater finds new version automatically it downloads it.
        CHECK_DOWNLOAD

    }

    public enum Result
    {

        UPDATE_FOUND,

        NO_UPDATE,

        SUCCESS,

        FAILED,

        BAD_ID
    }

    /**
     * Get the result of the update.
     *
     * @return result of the update.
     * @see Result
     */
    public Result getResult()
    {
        waitThread();
        return result;
    }

    /**
     * Get the latest version from spigot.
     *
     * @return latest version.
     */
    public String getVersion()
    {
        waitThread();
        return version;
    }

    /**
     * Check if id of resource is valid
     *
     * @param link link of the resource
     * @return true if id of resource is valid
     */
    private boolean checkResource(String link)
    {
        try
        {
            URL url = new URL(link);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.addRequestProperty("User-Agent", USER_AGENT);

            int code = connection.getResponseCode();

            if(code != 200)
            {
                connection.disconnect();
                result = Result.BAD_ID;
                return false;
            }
            connection.disconnect();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Checks if there is any update available.
     */
    private void checkUpdate()
    {
        try
        {
            String page = Integer.toString(this.page);

            URL url = new URL(API_RESOURCE+id+VERSIONS+PAGE+page);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.addRequestProperty("User-Agent", USER_AGENT);

            InputStream inputStream = connection.getInputStream();
            InputStreamReader reader = new InputStreamReader(inputStream);

            JsonElement element = new JsonParser().parse(reader);
            JsonArray jsonArray = element.getAsJsonArray();

            if(jsonArray.size() == 10 && !emptyPage)
            {
                connection.disconnect();
                this.page++;
                checkUpdate();
            }
            else if(jsonArray.size() == 0)
            {
                emptyPage = true;
                this.page--;
                checkUpdate();
            }
            else if(jsonArray.size() < 10)
            {
                element = jsonArray.get(jsonArray.size()-1);

                JsonObject object = element.getAsJsonObject();
                element = object.get("name");
                version = element.toString().replaceAll("\"", "").replace("v","");
                if(logger)
                    logs.info("Checking for update...");
                if(shouldUpdate(version, currentVersion) && updateType == UpdateType.VERSION_CHECK)
                {
                    result = Result.UPDATE_FOUND;
                    if(logger)
                        logs.info("Update found!");
                }
                else if(updateType == UpdateType.DOWNLOAD)
                {
                    if(logger)
                        logs.info("Trying to download update..");
                    download();
                }
                else if(updateType == UpdateType.CHECK_DOWNLOAD)
                {
                    if(shouldUpdate(version, currentVersion))
                    {
                        if(logger)
                            logs.info("Update found, downloading now...");
                        download();
                    }
                    else
                    {
                        if(logger)
                            logs.info("You are using latest version of the plugin.");
                        result = Result.NO_UPDATE;
                    }
                }
                else
                {
                    if(logger)
                        logs.info("You are using latest version of the plugin.");
                    result = Result.NO_UPDATE;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Checks if plugin should be updated
     * @param newVersion remote version
     * @param oldVersion current version
     */
    private boolean shouldUpdate(String newVersion, String oldVersion)
    {
        return !newVersion.equalsIgnoreCase(oldVersion);
    }

    /**
     * Downloads the file
     */
    private void download()
    {
        BufferedInputStream in = null;
        FileOutputStream fout = null;

        try
        {
            URL url = new URL(downloadLink);
            in = new BufferedInputStream(url.openStream());

            fout = new FileOutputStream(new File(updateFolder, file.getName()));

            final byte[] data = new byte[4096];
            int count;
            while ((count = in.read(data, 0, 4096)) != -1) {
                fout.write(data, 0, count);
            }
        }
        catch (Throwable ignored)
        {
            if(logger)
                logs.info("Can't download latest version automatically, download it manually from website.");
                logs.info(" ");
            result = Result.FAILED;
        }
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (final Throwable throwable) {
                logs.error("Can't download");
                logs.error(throwable);
            }
            try {
                if (fout != null) {
                    fout.close();
                }
            } catch (final Throwable throwable) {
                logs.error("Can't download");
                logs.error(throwable);
            }
        }
    }

    /**
     * Updater depends on thread's completion, so it is necessary to wait for thread to finish.
     */
    private void waitThread()
    {
        if(thread != null && thread.isAlive())
        {
            try
            {
                thread.join();
            } catch (Throwable throwable) {
                logs.error("Can't download");
                logs.error(throwable);
            }
        }
    }

    public class UpdaterRunnable implements Runnable
    {

        public void run() {
            if(checkResource(downloadLink))
            {
                downloadLink = downloadLink + DOWNLOAD;
                checkUpdate();
            }
        }
    }
}
