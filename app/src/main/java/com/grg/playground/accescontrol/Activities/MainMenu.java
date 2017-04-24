package com.grg.playground.accescontrol.Activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.content.Context;
import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;

import com.grg.playground.accescontrol.Common;
import com.grg.playground.accescontrol.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by playground on 29/01/2017.
 */

public class MainMenu extends Activity {

    private static final String LOG_TAG =
            MainMenu.class.getSimpleName();

    private static final int REQUEST_WRITE_STORAGE_CODE = 1;

    private AlertDialog mEnableNfc;
    private Intent mOldIntent = null;

    private boolean mResume = true;

    private Button mReadTag;

//*****************************************//

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);


        mReadTag = (Button) findViewById(R.id.buttonMainReadTag);


        // Check if the user granted the app write permissions.
        if (Common.hasWritePermissionToExternalStorage(this)) {
            initFolders();
        } else {
            // Request the permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE_CODE);
        }

        // Check if there is an NFC hardware component.
        Common.setNfcAdapter(NfcAdapter.getDefaultAdapter(this));
        if (Common.getNfcAdapter() == null) {
            Log.i("Get here:----->", "----------------> BAD BAD BAD!!! ");
            //-- future feature createNfcEnableDialog();
            //-- mEnableNfc.show();
            //-- mReadTag.setEnabled(false);
            //-- mWriteTag.setEnabled(false);
            //-- mResume = false;
        }

        // Check if mifare Classic supported
        if (!Common.useAsEditorOnly() && !Common.hasMifareClassicSupport()) {
            Log.i("GOT HERE!!!=====>", " ================> No mifare classic support found.... ");
        }

        // check if this is it...
       // checkNfc();
    }

    @Override
    public void onNewIntent(Intent intent) {
        Log.i("New tag ready:","-------------------------> Let's read new tag here");
        int typeCheck = Common.treatAsNewTag(intent, this);
        if (typeCheck == -1 || typeCheck == -2) {
            // Device or tag does not support MIFARE Classic.
            // Run the only thing that is possible: The tag info tool.
            Intent i = new Intent(this, TagInfoTool.class);
            startActivity(i);
        }
    }

    private void checkNfc() {
        // Check if the NFC hardware is enabled.
        if (Common.getNfcAdapter() != null
                && !Common.getNfcAdapter().isEnabled()) {
            // NFC is disabled.
            // Use as editor only?
            if (!Common.useAsEditorOnly()) {
                //  Show dialog.
                if (mEnableNfc == null) {
                    Log.i("Show alert dialog!", " NFC is disabled - switch it on !!!!");
                }
            }
            // Disable read/write tag options.
            // here buttons disable
        } else {
            // NFC is enabled. Hide dialog and enable NFC
            // foreground dispatch.
            if (mOldIntent != getIntent()) {
                int typeCheck = Common.treatAsNewTag(getIntent(), this);
                if (typeCheck == -1 || typeCheck == -2) {
                    // Device or tag does not support MIFARE Classic.
                    // Run the only thing that is possible: The tag info tool.


                    Intent i = new Intent(this, TagInfoTool.class);
                    Log.i("TEST error", "Got here! ---+++++++++-----");
                    startActivity(i);
                }
                mOldIntent = getIntent();
            }
            Log.i("Got here!"," Got here!");
            Common.enableNfcForegroundDispatch(this);
            Common.setUseAsEditorOnly(false);
            if (mEnableNfc == null) {
               //-- Log.i("Enable NFC dialog", "Here is hardware swidched off - call enable dialog here! ***************");
            }
        }
    }

    /**
     * If resuming is allowed because all dependencies from
     * {@link #onCreate(Bundle)} are satisfied, call
     * {@link #checkNfc()}
     * @see #onCreate(Bundle)
     * @see #checkNfc()
     */
    @Override
    public void onResume() {
        super.onResume();

        if (Common.hasWritePermissionToExternalStorage(this)) {
            if (!Common.hasMifareClassicSupport() || Common.useAsEditorOnly()) {
                // dump and key editor enable here
            } else {
                // todo: enable buttong here
            }
        } else {
            // TODO: disable buttons here
        }

        if (mResume) {
            checkNfc();
        }
    }

    /**
     * Disable NFC foreground dispatch system.
     * @see Common#disableNfcForegroundDispatch(Activity)
     */
    @Override
    public void onPause() {
        super.onPause();
        Common.disableNfcForegroundDispatch(this);
    }

    /**
     * Show the {@link ReadTag}.
     * @param view The View object that triggered the method
     * (in this case the read tag button).
     * @see ReadTag
     */
    public void onShowReadTag(View view) {
        Intent intent = new Intent(this, ReadTag.class);
        startActivity(intent);
       // Log.i("TEST error", "Got here! ---+++++++++-----");
    }

    /**
     * Create the directories needed by MCT and clean out the tmp folder.
     */
    private void initFolders() {
        if (Common.isExternalStorageWritableErrorToast(this)) {
            // Create keys directory.
            File path = new File(Environment.getExternalStoragePublicDirectory(
                    Common.HOME_DIR) + "/" + Common.KEYS_DIR);

            if (!path.exists() && !path.mkdirs()) {
                // Could not create directory.
                Log.e(LOG_TAG, "Error while creating '" + Common.HOME_DIR
                        + "/" + Common.KEYS_DIR + "' directory.");
                return;
            }

            // Create dumps directory.
            path = new File(Environment.getExternalStoragePublicDirectory(
                    Common.HOME_DIR) + "/" + Common.DUMPS_DIR);
            if (!path.exists() && !path.mkdirs()) {
                // Could not create directory.
                Log.e(LOG_TAG, "Error while creating '" + Common.HOME_DIR
                        + "/" + Common.DUMPS_DIR + "' directory.");
                return;
            }

            // Create tmp directory.
            path = new File(Environment.getExternalStoragePublicDirectory(
                    Common.HOME_DIR) + "/" + Common.TMP_DIR);
            if (!path.exists() && !path.mkdirs()) {
                // Could not create directory.
                Log.e(LOG_TAG, "Error while creating '" + Common.HOME_DIR
                        + Common.TMP_DIR + "' directory.");
                return;
            }
            // Clean up tmp directory.
            for (File file : path.listFiles()) {
                file.delete();
            }

            // Create std. key file if there is none.
            copyStdKeysFilesIfNecessary();
        }
    }

    /**
     * Copy the standard key files ({@link Common#STD_KEYS} and
     * {@link Common#STD_KEYS_EXTENDED}) form assets to {@link Common#KEYS_DIR}.
     * Key files are simple text files. Any plain text editor will do the trick.
     * All key and dump data from this App is stored in
     * getExternalStoragePublicDirectory(Common.HOME_DIR) to remain
     * there after App uninstallation.
     * @see Common#KEYS_DIR
     * @see Common#HOME_DIR
     * @see Common#copyFile(InputStream, OutputStream)
     */
    private void copyStdKeysFilesIfNecessary() {
        File std = new File(Environment.getExternalStoragePublicDirectory(
                Common.HOME_DIR) + "/" + Common.KEYS_DIR, Common.STD_KEYS);
        File extended = new File(Environment.getExternalStoragePublicDirectory(
                Common.HOME_DIR) + "/" + Common.KEYS_DIR,
                Common.STD_KEYS_EXTENDED);
        AssetManager assetManager = getAssets();
Log.i("Copy key file: ", "Copy key file if needed here_+_+_+_");
        if (!std.exists()) {
            // Copy std.keys.
            try {
                InputStream in = assetManager.open(
                        Common.KEYS_DIR + "/" + Common.STD_KEYS);
                OutputStream out = new FileOutputStream(std);
                Common.copyFile(in, out);
                in.close();
                out.flush();
                out.close();
            } catch(IOException e) {
                Log.e(LOG_TAG, "Error while copying 'std.keys' from assets "
                        + "to external storage.");
            }
        }
        if (!extended.exists()) {
            // Copy extended-std.keys.
            try {
                InputStream in = assetManager.open(
                        Common.KEYS_DIR + "/" + Common.STD_KEYS_EXTENDED);
                OutputStream out = new FileOutputStream(extended);
                Common.copyFile(in, out);
                in.close();
                out.flush();
                out.close();
            } catch(IOException e) {
                Log.e(LOG_TAG, "Error while copying 'extended-std.keys' "
                        + "from assets to external storage.");
            }
        }
    }
}
