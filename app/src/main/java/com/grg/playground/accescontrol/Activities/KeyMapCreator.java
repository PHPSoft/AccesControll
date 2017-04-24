package com.grg.playground.accescontrol.Activities;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.grg.playground.accescontrol.R;
import com.grg.playground.accescontrol.Common;
import com.grg.playground.accescontrol.MCReader;
import com.grg.playground.accescontrol.Activities.Preferences.Preference;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by playground on 30/01/2017.
 */

public class KeyMapCreator extends BasicActivity {

    public final static String EXTRA_KEYS_DIR =
            "com.grg.playground.accescontrol.Activity.KEYS_DIR";

    public final static String EXTRA_SECTOR_CHOOSER =
            "com.grg.playground.accescontrol.Activity.SECTOR_CHOOSER";

    public final static String EXTRA_SECTOR_CHOOSER_FROM =
            "com.grg.playground.accescontrol.Activity.SECTOR_CHOOSER_FROM";

    public final static String EXTRA_SECTOR_CHOOSER_TO =
            "com.grg.playground.accescontrol.Activity.SECTOR_CHOOSER_TO";

    public final static String EXTRA_BUTTON_TEXT =
            "com.grg.playground.accescontrol.Activity.BUTTON_TEXT";

    // Sector count of the biggest MIFARE Classic tag (4K Tag)
    public static final int MAX_SECTOR_COUNT = 40;
    // Block count of the biggest sector (4K Tag, Sector 32-39)
    public static final int MAX_BLOCK_COUNT_PER_SECTOR = 16;

    private static final String LOG_TAG =
            KeyMapCreator.class.getSimpleName();

    private static final int DEFAULT_SECTOR_RANGE_FROM = 0;
    private static final int DEFAULT_SECTOR_RANGE_TO = 15;

    private boolean mIsCreatingKeyMap;

    private File mKeyDirPath;
    private final Handler mHandler = new Handler();

    private TextView mSectorRange;
    private LinearLayout mKeyFilesGroup;

    private int mFirstSector;
    private int mLastSector;

    private int mProgressStatus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_key_map);


        mSectorRange = (TextView) findViewById(R.id.textViewCreateKeyMapFromTo);
        mKeyFilesGroup = (LinearLayout) findViewById(R.id.linearLayoutCreateKeyMapKeyFiles);

    }

    /**
     * Cancel the mapping process and disable NFC foreground dispatch system.
     * This method is not called, if screen orientation changes.
     * @see Common#disableNfcForegroundDispatch(Activity)
     */
    @Override
    public void onPause() {
        super.onPause();
        mIsCreatingKeyMap = false;
    }

    /**
     * List files from the {@link #EXTRA_KEYS_DIR} and select the last used
     * ones if {@link Preference#SaveLastUsedKeyFiles} is enabled.
     */
    @Override
    public void onStart() {
        super.onStart();

        if (mKeyDirPath == null) {
            // Is there a key directory in the Intent?f
            if (!getIntent().hasExtra(EXTRA_KEYS_DIR)) {
                setResult(2);
                finish();
                return;
            }
            String path = getIntent().getStringExtra(EXTRA_KEYS_DIR);

            // Is path null?
            if (path == null) {
                setResult(4);
                finish();
                return;
            }
            mKeyDirPath = new File(path);
        }

        // Is external storage writable?
        if (!Common.isExternalStorageWritableErrorToast(this)) {
            setResult(3);
            finish();
            return;
        }

        // List key files and select last used (if corresponding
        // setting is active).
        boolean selectLastUsedKeyFiles = Common.getPreferences().getBoolean(
                Preference.SaveLastUsedKeyFiles.toString(), true);

        ArrayList<String> selectedFiles = null;
        if (selectLastUsedKeyFiles) {
            SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
            // All previously selected key files are stored in one string
            // separated by "/".
            String selectedFilesChain = sharedPref.getString(
                    "last_used_key_files", null);
            if (selectedFilesChain != null) {
                selectedFiles = new ArrayList<String>(
                        Arrays.asList(selectedFilesChain.split("/")));
            }
        }

        File[] keyFiles = mKeyDirPath.listFiles();

        Arrays.sort(keyFiles);

        mKeyFilesGroup.removeAllViews();

        ArrayList<String> fileNames = new ArrayList<String>();

        for(File f : keyFiles) {
            fileNames.add(f.getName().toString());
        }

        Log.i("Files selected: ", "Files --------==========--------- selected: " + fileNames);
        onCreateKeyMap(fileNames);
    }


    /**
     * Create a key map and save it to
     * {@link Common#setKeyMap(android.util.SparseArray)}.
     * For doing so it uses other methods (
     * {@link #createKeyMap(MCReader, Context)},
     * {@link #keyMapCreated(MCReader)}).
     * If {@link Preference#SaveLastUsedKeyFiles} is active, this will also
     * save the selected key files.
     * (in this case the map keys to sectors button).
     * @see #createKeyMap(MCReader, Context)
     * @see #keyMapCreated(MCReader)
     */
    public void onCreateKeyMap(ArrayList<String>  fileNames) {
        boolean saveLastUsedKeyFiles = Common.getPreferences().getBoolean(
                Preference.SaveLastUsedKeyFiles.toString(), true);
        String lastSelectedKeyFiles = "";
        // Check for checked check boxes.
   /*     ArrayList<String> fileNames = new ArrayList<String>();
        for (int i = 0; i < mKeyFilesGroup.getChildCount(); i++) {
            CheckBox c = (CheckBox) mKeyFilesGroup.getChildAt(i);
            if (c.isChecked()) {
                fileNames.add(c.getText().toString());
            }
        } */
        if (fileNames.size() > 0) {
            // Check if key files still exists.
            ArrayList<File> keyFiles = new ArrayList<File>();
            for (String fileName : fileNames) {
                File keyFile = new File(mKeyDirPath, fileName);
                if (keyFile.exists()) {
                    // Add key file.
                    keyFiles.add(keyFile);
                    if (saveLastUsedKeyFiles) {
                        lastSelectedKeyFiles += fileName + "/";
                    }
                } else {
                    Log.d(LOG_TAG, "Key file "
                            + keyFile.getAbsolutePath()
                            + "doesn't exists anymore.");
                }
            }
            if (keyFiles.size() > 0) {
                // Save last selected key files as "/"-separated string
                // (if corresponding setting is active).
                if (saveLastUsedKeyFiles) {
                    SharedPreferences sharedPref = getPreferences(
                            Context.MODE_PRIVATE);
                    SharedPreferences.Editor e = sharedPref.edit();
                    e.putString("last_used_key_files",
                            lastSelectedKeyFiles.substring(
                                    0, lastSelectedKeyFiles.length() - 1));
                    e.apply();
                }

                // Create reader.
                MCReader reader = Common.checkForTagAndCreateReader(this);
                if (reader == null) {
                    return;
                }

                // Set key files.
                File[] keys = keyFiles.toArray(new File[keyFiles.size()]);
                if (!reader.setKeyFile(keys, this)) {
                    // Error.
                    reader.close();
                    return;
                }
                // Don't turn screen of while mapping.
                getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                // Get key map range.
                if (mSectorRange.getText().toString().equals(
                        getString(R.string.text_sector_range_all))) {
                    // Read all.
                    mFirstSector = 0;
                    mLastSector = reader.getSectorCount()-1;
                } else {
                    String[] fromAndTo = mSectorRange.getText()
                            .toString().split(" ");
                    mFirstSector = Integer.parseInt(fromAndTo[0]);
                    mLastSector = Integer.parseInt(fromAndTo[2]);
                }
                // Set map creation range.
                if (!reader.setMappingRange(
                        mFirstSector, mLastSector)) {
                    // Error.
                    Toast.makeText(this,
                            R.string.info_mapping_sector_out_of_range,
                            Toast.LENGTH_LONG).show();
                    reader.close();
                    return;
                }
                Common.setKeyMapRange(mFirstSector, mLastSector);
                // Init. GUI elements.
                mProgressStatus = -1;
                mIsCreatingKeyMap = true;
                Toast.makeText(this, R.string.info_wait_key_map,
                        Toast.LENGTH_SHORT).show();
                // Read as much as possible with given key file.
                createKeyMap(reader, this);
            }
        }
    }

    /**
     * method starts a worker thread that first creates a key map and then
     * calls {@link #keyMapCreated(MCReader)}.
     * It also updates the progress bar in the UI thread.
     * @param reader A connected {@link MCReader}.
     * @see #keyMapCreated(MCReader)
     */
    private void createKeyMap(final MCReader reader, final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Build key map parts and update the progress bar.
                while (mProgressStatus < mLastSector) {
                    mProgressStatus = reader.buildNextKeyMapPart();
                    if (mProgressStatus == -1 || !mIsCreatingKeyMap) {
                        // Error while building next key map part.
                        break;
                    }
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        getWindow().clearFlags(
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        reader.close();
                        if (mIsCreatingKeyMap && mProgressStatus != -1) {
                            keyMapCreated(reader);
                        } else {
                            // Error during key map creation.
                            Common.setKeyMap(null);
                            Common.setKeyMapRange(-1, -1);
                            Toast.makeText(context, R.string.info_key_map_error,
                                    Toast.LENGTH_LONG).show();
                        }
                        mIsCreatingKeyMap = false;
                    }
                });
            }
        }).start();
    }

    /**
     * Triggered by {@link #createKeyMap(MCReader, Context)}, this method
     * sets the result code to {@link Activity#RESULT_OK},
     * saves the created key map to
     * {@link Common#setKeyMap(android.util.SparseArray)}
     * and finishes this Activity.
     * @param reader A {@link MCReader}.
     * @see #createKeyMap(MCReader, Context)
     */
    private void keyMapCreated(MCReader reader) {
        // LOW: Return key map in intent.
        if (reader.getKeyMap().size() == 0) {
            Common.setKeyMap(null);
            // Error. No valid key found.
            Toast.makeText(this, R.string.info_no_key_found,
                    Toast.LENGTH_LONG).show();
        } else {
            Common.setKeyMap(reader.getKeyMap());
//            Intent intent = new Intent();
//            intent.putExtra(EXTRA_KEY_MAP, mMCReader);
//            setResult(Activity.RESULT_OK, intent);
            setResult(Activity.RESULT_OK);
            finish();
        }

    }
}
