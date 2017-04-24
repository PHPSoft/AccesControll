package com.grg.playground.accescontrol.Activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.SpannableString;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.grg.playground.accescontrol.MCrypt;
import com.grg.playground.accescontrol.Common;
import com.grg.playground.accescontrol.MCReader;
import com.grg.playground.accescontrol.R;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;



/**
 * Created by playground on 30/01/2017.
 */

public class ReadTag extends Activity {

    private final static int KEY_MAP_CREATOR = 1;

    private final Handler mHandler = new Handler();
    private SparseArray<String[]> mRawDump;

    private TextView readName;
    private TextView readSurname;
    private TextView readCompany;
    private ProgressBar mProgress;
    private TextView mViewReadTag;
    private TextView readBadgeid;
    //--private TextView readRfuid;
    private Button mBackMainMenu;


    /**
     * Check for external storage and show the {@link KeyMapCreator}.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_read_tag);

        if (!Common.isExternalStorageWritableErrorToast(this)) {
            finish();
            return;
        }

        readName = (TextView) findViewById(R.id.readName);
        readSurname = (TextView) findViewById(R.id.readSurname);
        readCompany = (TextView) findViewById(R.id.readCompany);
        mViewReadTag = (TextView) findViewById(R.id.textViewReadTag);
        readBadgeid = (TextView) findViewById(R.id.readBadgeid);
      //--  readRfuid = (TextView) findViewById(R.id.readRfuid);
        mBackMainMenu = (Button) findViewById(R.id.backMainMenu);



        mProgress = (ProgressBar) findViewById(R.id.progressBarReadTag);


        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR,
                Environment.getExternalStoragePublicDirectory(
                        Common.HOME_DIR) + "/" + Common.KEYS_DIR);
        intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT,
                getString(R.string.action_create_key_map_and_read));
        startActivityForResult(intent, KEY_MAP_CREATOR);
        Log.i("onCreate: ", "Got here! ---+++???+++----- oncreate");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i("onActivityResult: ", "Got here! ---+++???+++----- onActiviryResult");
        switch(requestCode) {
            case KEY_MAP_CREATOR:
                if (resultCode != Activity.RESULT_OK) {
                    // Error.
                    if (resultCode == 4) {
                        // Error. Path from the calling intend was null.
                        // (This is really strange and should not occur.)
                        Toast.makeText(this, R.string.info_strange_error,
                                Toast.LENGTH_LONG).show();
                    }
                    finish();
                    return;
                } else {
                    Log.i("read tag OK!","READ TaG OK HERE!!!!!!!");
                    // Read Tag.
                    readTag();
                }
                break;
        }
    }

    /**
     * Triggered by {@link #onActivityResult(int, int, Intent)}
     * this method starts a worker thread that first reads the tag and then
     * calls {@link #createTagDump(SparseArray)}.
     */
    private void readTag() {
        final MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Get key map from glob. variable.

                Log.i("KEY MAP: ", "Key map: " + Common.getKeyMap());
                mRawDump = reader.readAsMuchAsPossible(
                        Common.getKeyMap());

                reader.close();

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        createTagDump(mRawDump);
                    }
                });
            }
        }).start();
    }

    private void createTagDump(SparseArray<String[]> rawDump) {
        ArrayList<String> tmpDump = new ArrayList<String>();

        if (rawDump != null) {
            if (rawDump.size() != 0) {
                for (int i = Common.getKeyMapRangeFrom();
                     i <= Common.getKeyMapRangeTo(); i++) {
                    String[] val = rawDump.get(i);
                    // Mark headers (sectors) with "+".
                    tmpDump.add("+Sector: " + i);
                    if (val != null ) {
                        Collections.addAll(tmpDump, val);
                    } else {
                        // Mark sector as not readable ("*").
                        tmpDump.add("*No keys found or dead sector");
                    }
                }
                String[] dump = tmpDump.toArray(new String[tmpDump.size()]);

                int err = Common.isValidDump(dump, true);

                if (err == 0) {
                    initDumpData(dump);
                }

                /*
                // Show Dump Editor Activity.
                Intent intent = new Intent(this, DumpEditor.class);
                intent.putExtra(DumpEditor.EXTRA_DUMP, dump);
                startActivity(intent);
                */
            } else {
                // Error, keys from key map are not valid for reading.
                Toast.makeText(this, R.string.info_none_key_valid_for_reading,
                        Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, R.string.info_tag_removed_while_reading,
                    Toast.LENGTH_LONG).show();
        }
       // finish();
        mProgress.setVisibility(View.GONE);
        mViewReadTag.setVisibility(View.GONE);

    }


    private void initDumpData (String[] lines) {
        boolean isFirstBlock = false;
        boolean showName = false;
        boolean showSurname = false;
        boolean showCompany = false;
        boolean showBadgeid = false;
        boolean showRfuid = false;

        String delegate = "";

        ArrayList<SpannableString> blocks =
                new ArrayList<SpannableString>(4);

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("+")) {
                // Line is a header.
                isFirstBlock = lines[i].endsWith(" 0");
                String sectorNumber = lines[i].split(": ")[1]; //-- output sector number
Log.i("Sector number: " , "Sector number: " + sectorNumber);

                if (sectorNumber.equals("2")) {
                    showName = true;
                }

                if (sectorNumber.equals("3")) {
                    showSurname = true;
                }

                if (sectorNumber.equals("4")) {
                    showCompany = true;
                }

                if (sectorNumber.equals("9")) {
                    showBadgeid = true;
                }

                if (sectorNumber.equals("10")) {
                    showRfuid = true;
                }

                Log.i("showName", "ShowNAme = " + showName);
                Log.i("showSurname", "showSurname = " + showSurname);
                Log.i("showCompany", "showCompany = " + showCompany);


                // Add sector data (EditText) if not at the end and if the
                // next line is not an error line ("*").
            } else if (lines[i].startsWith("*")){
                Log.i("Error Line: ", "Line is a sector that could not be read.");
            } else {
                // Line is a block.
                if (i+1 == lines.length || lines[i+1].startsWith("+")) {
                    // Line is a sector trailer.
                    //--blocks.add(colorSectorTrailer(lines[i]));
                    // Add sector data to the EditText.
                    CharSequence text = "";
                   int j;
                    for (j = 0; j < blocks.size()-1; j++) {
                        text = TextUtils.concat(text, blocks.get(j));
                    }
                    text = TextUtils.concat(text, blocks.get(j));
                    // -- where data output


                    // try to decrypt
                    MCrypt crypt = new MCrypt();
                    try {
                        Log.i("HEX block","text: " + text.toString());

                        //-- decrypt HEX to ASCII:
                        //-- byte[] hextoString = crypt.decrypt(text.toString());

                        //-- plain text

                        byte[] hextoString = crypt.hexToBytes(text.toString());


                        String decryptText = new String(hextoString);

                       // String s = URLDecoder.decode(decryptText, "UTF-8");
                        Log.i("TEST STRING TWO", "Byte to string: " + decryptText);
                       // Log.i("TEST STRING TWO", "Byte to string: " + s);

                        if (showName) {
                            showName = false;
                            delegate = delegate.concat(decryptText);

                        }

                        if (showSurname) {
                            showSurname = false;
                            delegate = delegate.concat(decryptText);

                         /// stpid way to do this BUT!!!
Log.i("whole string:", "DELEGATE: " + delegate);


                            String[] separated = delegate.split("\\|");
                            readName.setText(separated[0].trim().substring(2));
                            readSurname.setText(separated[1].trim());
                            readBadgeid.setText(separated[2].trim());
                            readCompany.setText(separated[3].trim());

                        }

                        if (showCompany) {
                            showCompany = false;
                          //  readCompany.setText(decryptText);
                        }

                        if (showBadgeid) {
                            showBadgeid = false;
                           //readBadgeid.setText(decryptText);
                        }

                        if (showRfuid) {
                            showRfuid = false;
                           // readRfuid.setText(decryptText);
                        }




                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    blocks = new ArrayList<SpannableString>(4);
                } else {
                    // Add data block.
                    blocks.add(colorDataBlock(lines[i], isFirstBlock));
                    isFirstBlock = false;
                }
            }
        }
    }

    private SpannableString colorDataBlock(String data, boolean hasUID) {
        SpannableString ret;
        if (hasUID) {
            // First block (UID, manuf. data).
            ret = new SpannableString(TextUtils.concat(
                    Common.colorString(data,
                            getResources().getColor(R.color.purple))));
        } else {
            if (Common.isValueBlock(data)) {
                // Value block.
                ret = Common.colorString(data,
                        getResources().getColor(R.color.yellow));
            } else {
                // Just data.
                ret = new SpannableString(data);
            }
        }
        return ret;
    }

    public String convertHexToString(String hex){

        StringBuilder sb = new StringBuilder();
        StringBuilder temp = new StringBuilder();

        //49204c6f7665204a617661 split into two characters 49, 20, 4c...
        for( int i=0; i<hex.length()-1; i+=2 ){

            //grab the hex in pairs
            String output = hex.substring(i, (i + 2));
            //convert hex to decimal
            int decimal = Integer.parseInt(output, 16);
            //convert the decimal to character
            sb.append((char)decimal);

            temp.append(decimal);
        }
        System.out.println("Decimal : " + temp.toString());

        return sb.toString();
    }

    public void onBackToMainMenu(View view)
    {
        finish();
    }


    private SpannableString colorSectorTrailer(String data) {
        // Get sector trailer colors.
     /*   int colorKeyA = getResources().getColor(
                R.color.light_green);
        int colorKeyB = getResources().getColor(
                R.color.dark_green);
        int colorAC = getResources().getColor(
                R.color.orange);
        try {
            SpannableString keyA = Common.colorString(
                    data.substring(0, 12), colorKeyA);
            SpannableString keyB = Common.colorString(
                    data.substring(20), colorKeyB);
            SpannableString ac = Common.colorString(
                    data.substring(12, 20), colorAC);
            return new SpannableString(
                    TextUtils.concat(keyA, ac, keyB));
        } catch (IndexOutOfBoundsException e) {
            Log.d("ERROR", "Error while coloring " +
                    "sector trailer");
        } */
        return new SpannableString(data);
    }
}
