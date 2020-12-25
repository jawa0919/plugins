package io.flutter.plugins.webviewflutter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FileChooser {

    private final Activity activity;
    public ResultHandler resultHandler;

    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadMessageArray;
    private final static int FILECHOOSER_RESULTCODE = 1;
    private Uri fileUri;
    private Uri videoUri;

    FileChooser(Activity activity) {
        this.activity = activity;
        this.resultHandler = new ResultHandler();
    }

    class ResultHandler {
        public boolean handleResult(int requestCode, int resultCode, Intent intent) {
            boolean handled = false;
            if (Build.VERSION.SDK_INT >= 21) {
                if (requestCode == FILECHOOSER_RESULTCODE) {
                    Uri[] results = null;
                    if (resultCode == Activity.RESULT_OK) {
                        if (fileUri != null && getFileSize(fileUri) > 0) {
                            results = new Uri[]{fileUri};
                        } else if (videoUri != null && getFileSize(videoUri) > 0) {
                            results = new Uri[]{videoUri};
                        } else if (intent != null) {
                            results = getSelectedFiles(intent);
                        }
                    }
                    if (mUploadMessageArray != null) {
                        mUploadMessageArray.onReceiveValue(results);
                        mUploadMessageArray = null;
                    }
                    handled = true;
                }
            } else {
                if (requestCode == FILECHOOSER_RESULTCODE) {
                    Uri result = null;
                    if (resultCode == Activity.RESULT_OK && intent != null) {
                        result = intent.getData();
                    }
                    if (mUploadMessage != null) {
                        mUploadMessage.onReceiveValue(result);
                        mUploadMessage = null;
                    }
                    handled = true;
                }
            }
            return handled;
        }
    }

    public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
    }

    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        if (mUploadMessageArray != null) {
            mUploadMessageArray.onReceiveValue(null);
        }
        mUploadMessageArray = filePathCallback;
        final String[] acceptTypes = getSafeAcceptedTypes(fileChooserParams);
        List<Intent> intentList = new ArrayList<Intent>();
        fileUri = null;
        videoUri = null;
        if (acceptsImages(acceptTypes)) {
            Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            fileUri = getOutputFilename(MediaStore.ACTION_IMAGE_CAPTURE);
            takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
            intentList.add(takePhotoIntent);
        }
        if (acceptsVideo(acceptTypes)) {
            Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            videoUri = getOutputFilename(MediaStore.ACTION_VIDEO_CAPTURE);
            takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
            intentList.add(takeVideoIntent);
        }
        Intent contentSelectionIntent;
        if (Build.VERSION.SDK_INT >= 21) {
            final boolean allowMultiple = fileChooserParams.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
            contentSelectionIntent = fileChooserParams.createIntent();
            contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
        } else {
            contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
            contentSelectionIntent.setType("*/*");
        }
        Intent[] intentArray = intentList.toArray(new Intent[intentList.size()]);

        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
        activity.startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
        return true;
    }

    private String[] getSafeAcceptedTypes(WebChromeClient.FileChooserParams params) {

        // the getAcceptTypes() is available only in api 21+
        // for lower level, we ignore it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return params.getAcceptTypes();
        }

        final String[] EMPTY = {};
        return EMPTY;
    }

    private Boolean acceptsImages(String[] types) {
        return isArrayEmpty(types) || arrayContainsString(types, "image");
    }

    private Boolean acceptsVideo(String[] types) {
        return isArrayEmpty(types) || arrayContainsString(types, "video");
    }

    private Boolean arrayContainsString(String[] array, String pattern) {
        for (String content : array) {
            if (content.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private Boolean isArrayEmpty(String[] arr) {
        // when our array returned from getAcceptTypes() has no values set from the
        // webview
        // i.e. <input type="file" />, without any "accept" attr
        // will be an array with one empty string element, afaik
        return arr.length == 0 || (arr.length == 1 && arr[0].length() == 0);
    }

    private Uri getOutputFilename(String intentType) {
        String prefix = "";
        String suffix = "";

        if (intentType == MediaStore.ACTION_IMAGE_CAPTURE) {
            prefix = "image-";
            suffix = ".jpg";
        } else if (intentType == MediaStore.ACTION_VIDEO_CAPTURE) {
            prefix = "video-";
            suffix = ".mp4";
        }

        String packageName = activity.getPackageName();
        File capturedFile = null;
        try {
            capturedFile = createCapturedFile(prefix, suffix);
        } catch (IOException e) {
            e.printStackTrace();
        }
        String authority = packageName + ".io.flutter.plugins.webviewflutter.fileprovider";
        return FileProvider.getUriForFile(activity, authority, capturedFile);
    }

    @SuppressLint("SimpleDateFormat")
    private File createCapturedFile(String prefix, String suffix) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = prefix + "_" + timeStamp;
        File storageDir = activity.getExternalFilesDir(null);
        return File.createTempFile(imageFileName, suffix, storageDir);
    }

    private long getFileSize(Uri fileUri) {
        Cursor returnCursor = activity.getContentResolver().query(fileUri, null, null, null, null);
        returnCursor.moveToFirst();
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        return returnCursor.getLong(sizeIndex);
    }

    private Uri[] getSelectedFiles(Intent data) {
        // we have one files selected
        if (data.getData() != null) {
            String dataString = data.getDataString();
            if (dataString != null) {
                return new Uri[]{Uri.parse(dataString)};
            }
        }
        // we have multiple files selected
        if (data.getClipData() != null) {
            final int numSelectedFiles = data.getClipData().getItemCount();
            Uri[] result = new Uri[numSelectedFiles];
            for (int i = 0; i < numSelectedFiles; i++) {
                result[i] = data.getClipData().getItemAt(i).getUri();
            }
            return result;
        }
        return null;
    }
}
