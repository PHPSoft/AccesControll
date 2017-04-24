package com.grg.playground.accescontrol;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.NfcA;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

public class Common extends Application {

    public static final String HOME_DIR = "/AccessControl";
    public static final String KEYS_DIR = "key-files";

    /**
     * If NFC is disabled and the user chose to use MCT in editor only mode,
     * the choice is remembered here.
     */
    private static boolean mUseAsEditorOnly = false;

    /**
     * 1 if the device does support MIFARE Classic. -1 if it doesn't support
     * it. 0 if the support check was not yet performed.
     * Checking for MIFARE Classic support is really expensive. Therefore
     * remember the result here.
     */
    private static int mHasMifareClassicSupport = 0;

    private static NfcAdapter mNfcAdapter;
    private static Context mAppContext;
    private static float mScale;

    private static Tag mTag = null;
    private static byte[] mUID = null;

    private static final String LOG_TAG = Common.class.getSimpleName();

    private static SparseArray<byte[][]> mKeyMap = null;

    private static int mKeyMapFrom = -1;

    private static int mKeyMapTo = -1;

    public static final String DUMPS_DIR = "dump-files";

    /**
     * (sub directory of {@link #HOME_DIR}.)
     */
    public static final String TMP_DIR = "tmp";

    /**
     * This file contains some standard MIFARE keys.
     * <ul>
     * <li>0xFFFFFFFFFFFF - Un-formatted, factory fresh tags.</li>
     * <li>0xA0A1A2A3A4A5 - First sector of the tag (MIFARE MAD).</li>
     * <li>0xD3F7D3F7D3F7 - NDEF formatted tags.</li>
     * </ul>
     */
    public static final String STD_KEYS = "std.keys";

    public static final String STD_KEYS_EXTENDED = "extended-std.keys";


    /**
     * Initialize the {@link #mAppContext} with the application context
     * (for {@link #getPreferences()}).
     */
    @Override
    public void onCreate() {
        super.onCreate();
        mAppContext = getApplicationContext();

        Log.i("App Context = ", "Context: " + mAppContext);

        mScale = getResources().getDisplayMetrics().density;
    }

    /**
     * Check if the user granted read/write permissions to the external storage.
     * @param context The Context to check the permissions for.
     * @return True if granted the permissions. False otherwise.
     */
    public static boolean hasWritePermissionToExternalStorage(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Set the App wide used NFC adapter.
     * @param nfcAdapter The NFC adapter that should be used.
     */
    public static void setNfcAdapter(NfcAdapter nfcAdapter) {
        mNfcAdapter = nfcAdapter;
    }

    /**
     * Get the App wide used NFC adapter.
     * @return NFC adapter.
     */
    public static NfcAdapter getNfcAdapter() {
        return mNfcAdapter;
    }

    /**
     * If NFC is disabled and the user chose to use MCT in editor only mode,
     * this method will return true.
     * @return True if the user wants to use MCT in editor only mode.
     * False otherwise.
     */
    public static boolean useAsEditorOnly() {
        return mUseAsEditorOnly;
    }

    public static boolean hasMifareClassicSupport() {
        if (mHasMifareClassicSupport != 0) {
            return mHasMifareClassicSupport == 1;
        }

        // Check if ther is any NFC hardware at all.
        if (NfcAdapter.getDefaultAdapter(mAppContext) == null) {
            mUseAsEditorOnly = true;
            mHasMifareClassicSupport = -1;
            return false;
        }

        File device = new File("/dev/bcm2079x-i2c");
        if (device.exists()) {
            mHasMifareClassicSupport = -1;
            return false;
        }

        device = new File("/dev/pn544");
        if (device.exists()) {
            mHasMifareClassicSupport = 1;
            return true;
        }

        File libsFolder = new File("/system/lib");
        File[] libs = libsFolder.listFiles();
        for (File lib : libs) {
            if (lib.isFile()
                    && lib.getName().startsWith("libnfc")
                    && lib.getName().contains("brcm")
                // Add here other non NXP NFC libraries.
                    ) {
                mHasMifareClassicSupport = -1;
                return false;
            }
        }

        mHasMifareClassicSupport = 1;
        return true;

    }

    /**
     * Get the shared preferences with application context for saving
     * and loading ("global") values.
     * @return The shared preferences object with application context.
     */
    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mAppContext);
    }

    public static void setTag(Tag tag) {
        mTag = tag;
        mUID = tag.getId();
    }

    public static String byte2HexString(byte[] bytes) {
        String ret = "";
        if (bytes != null) {
            for (Byte b : bytes) {
                ret += String.format("%02X", b.intValue() & 0xFF);
            }
        }
        return ret;
    }

    /**
     * For Activities which want to treat new Intents as Intents with a new
     * Tag attached. If the given Intent has a Tag extra, it will be patched
     * by {@link MCReader#patchTag(Tag)} and  {@link #mTag} as well as
     * {@link #mUID} will be updated. A Toast message will be shown in the
     * Context of the calling Activity. This method will also check if the
     * device/tag supports MIFARE Classic (see return values and
     * {@link #checkMifareClassicSupport(Tag, Context)}).
     * @param intent The Intent which should be checked for a new Tag.
     * @param context The Context in which the Toast will be shown.
     * @return
     * <ul>
     * <li>0 - The device/tag supports MIFARE Classic</li>
     * <li>-1 - Device does not support MIFARE Classic.</li>
     * <li>-2 - Tag does not support MIFARE Classic.</li>
     * <li>-3 - Error (tag or context is null).</li>
     * <li>-4 - Wrong Intent (action is not "ACTION_TECH_DISCOVERED").</li>
     * </ul>
     * @see #mTag
     * @see #mUID
     * @see #checkMifareClassicSupport(Tag, Context)
     */
    public static int treatAsNewTag(Intent intent, Context context) {
        // Check if Intent has a NFC Tag.
        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())) {
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            tag = MCReader.patchTag(tag);
            Log.i("CHECK TAG HERE:", " TAG FOUND: " + tag);
            setTag(tag);

            // Show Toast message with UID.
            String id = context.getResources().getString(
                    R.string.info_new_tag_found) + " (UID: ";
            id += byte2HexString(tag.getId());
            id += ")";
            Toast.makeText(context, id, Toast.LENGTH_LONG).show();
            return checkMifareClassicSupport(tag, context);
        }
        return -4;
    }

    /**
     * Check if the tag and the device support the MIFARE Classic technology.
     * @param tag The tag to check.
     * @param context The context of the package manager.
     * @return
     * <ul>
     * <li>0 - Device and tag support MIFARE Classic.</li>
     * <li>-1 - Device does not support MIFARE Classic.</li>
     * <li>-2 - Tag does not support MIFARE Classic.</li>
     * <li>-3 - Error (tag or context is null).</li>
     * </ul>
     */
    public static int checkMifareClassicSupport(Tag tag, Context context) {
        if (tag == null || context == null) {
            // Error.
            return -3;
        }

        if (Arrays.asList(tag.getTechList()).contains(
                MifareClassic.class.getName())) {
            // Device and tag support MIFARE Classic.
            return 0;

            // This is no longer valid. There are some devices (e.g. LG's F60)
            // that have this system feature but no MIFARE Classic support.
            // (The F60 has a Broadcom NFC controller.)
        /*
        } else if (context.getPackageManager().hasSystemFeature(
                "com.nxp.mifare")){
            // Tag does not support MIFARE Classic.
            return -2;
        */

        } else {
            // Check if device does not support MIFARE Classic.
            // For doing so, check if the ATQA + SAK of the tag indicate that
            // it's a MIFARE Classic tag.
            // See: http://www.nxp.com/documents/application_note/AN10833.pdf
            // (Table 5 and 6)
            // 0x28 is for some emulated tags.
            NfcA nfca = NfcA.get(tag);
            byte[] atqa = nfca.getAtqa();
            if (atqa[1] == 0 &&
                    (atqa[0] == 4 || atqa[0] == (byte)0x44 ||
                            atqa[0] == 2 || atqa[0] == (byte)0x42)) {
                // ATQA says it is most likely a MIFARE Classic tag.
                byte sak = (byte)nfca.getSak();
                if (sak == 8 || sak == 9 || sak == (byte)0x18 ||
                        sak == (byte)0x88 ||
                        sak == (byte)0x28) {
                    // SAK says it is most likely a MIFARE Classic tag.
                    // --> Device does not support MIFARE Classic.
                    return -1;
                }
            }
            // Nope, it's not the device (most likely).
            // The tag does not support MIFARE Classic.
            return -2;
        }
    }

    /**
     * Enables the NFC foreground dispatch system for the given Activity.
     * @param targetActivity The Activity that is in foreground and wants to
     * have NFC Intents.
     * @see #disableNfcForegroundDispatch(Activity)
     */
    public static void enableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {

            Intent intent = new Intent(targetActivity, targetActivity.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(targetActivity, 0, intent, 0);

            mNfcAdapter.enableForegroundDispatch(
                    targetActivity, pendingIntent, null, new String[][] {
                            new String[] { NfcA.class.getName() } });

        }
    }

    /**
     * Disable the NFC foreground dispatch system for the given Activity.
     * @param targetActivity An Activity that is in foreground and has
     * NFC foreground dispatch system enabled.
     * @see #enableNfcForegroundDispatch(Activity)
     */
    public static void disableNfcForegroundDispatch(Activity targetActivity) {
        if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {
            mNfcAdapter.disableForegroundDispatch(targetActivity);
        }
    }

    public static void setUseAsEditorOnly(boolean value) {
        mUseAsEditorOnly = value;
    }

    /**
     * Checks if external storage is available for read and write.
     * If not, show an error Toast.
     * @param context The Context in which the Toast will be shown.
     * @return True if external storage is writable. False otherwise.
     */
    public static boolean isExternalStorageWritableErrorToast(
            Context context) {
        if (isExternalStorageMounted()) {
            return true;
        }
        Toast.makeText(context, R.string.info_no_external_storage,
                Toast.LENGTH_LONG).show();
        return false;
    }

    public static boolean isExternalStorageMounted() {
        return Environment.MEDIA_MOUNTED.equals(
                Environment.getExternalStorageState());
    }

    public static MCReader checkForTagAndCreateReader(Context context) {
        MCReader reader;
        boolean tagLost = false;
        // Check for tag.
        Log.i("CARD ---> ", "Card here: " + mTag);

        if (mTag != null && (reader = MCReader.get(mTag)) != null) {
            try {
                reader.connect();
            } catch (Exception e) {
                tagLost = true;
            }
            if (!tagLost && !reader.isConnected()) {
                reader.close();
                tagLost = true;
            }
            if (!tagLost) {
                return reader;
            }
        }

        // Error. The tag is gone.
        Toast.makeText(context, R.string.info_no_tag_found,
                Toast.LENGTH_LONG).show();
        return null;
    }


    /**
     * Convert a string of hex data into a byte array.
     * Original author is: Dave L. (http://stackoverflow.com/a/140861).
     * @param s The hex string to convert
     * @return An array of bytes with the values of the string.
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        try {
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                        + Character.digit(s.charAt(i+1), 16));
            }
        } catch (Exception e) {
            Log.d(LOG_TAG, "Argument(s) for hexStringToByteArray(String s)"
                    + "was not a hex string");
        }
        return data;
    }

    /**
     * Get the key map generated by
     * {@link com.grg.playground.accescontrol.Activities.KeyMapCreator}.
     * @return A key map (see {@link MCReader#getKeyMap()}).
     */
    public static SparseArray<byte[][]> getKeyMap() {
        return mKeyMap;
    }

    /**
     * Get the key map start point.
     * @return {@link #mKeyMapFrom}
     */
    public static int getKeyMapRangeFrom() {
        return mKeyMapFrom;
    }

    /**
     * Get the key map end point
     * @return {@link #mKeyMapTo}
     */
    public static int getKeyMapRangeTo() {
        return mKeyMapTo;
    }

    /**
     * Copy file.
     * @param in Input file (source).
     * @param out Output file (destination).
     * @throws IOException
     */
    public static void copyFile(InputStream in, OutputStream out)
            throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    public static void setKeyMapRange (int from, int to){
        mKeyMapFrom = from;
        mKeyMapTo = to;
    }

    public static void setKeyMap(SparseArray<byte[][]> value) {
        mKeyMap = value;
    }


    /**
     * Read a file line by line. The file should be a simple text file.
     * Empty lines and lines STARTING with "#" will not be interpreted.
     * @param file The file to read.
     * @param readComments Whether to read comments or to ignore them.
     * Comments are lines STARTING with "#" (and empty lines).
     * @param context  The context in which the possible "Out of memory"-Toast
     * will be shown.
     * @return Array of strings representing the lines of the file.
     * If the file is empty or an error occurs "null" will be returned.
     */
    public static String[] readFileLineByLine(File file, boolean readComments,
                                              Context context) {
        BufferedReader br = null;
        String[] ret = null;
        if (file != null  && isExternalStorageMounted() && file.exists()) {
            try {
                br = new BufferedReader(new FileReader(file));

                String line;
                ArrayList<String> linesArray = new ArrayList<String>();
                while ((line = br.readLine()) != null)   {
                    // Ignore empty lines.
                    // Ignore comments if readComments == false.
                    if ( !line.equals("")
                            && (readComments || !line.startsWith("#"))) {
                        try {
                            linesArray.add(line);
                        } catch (OutOfMemoryError e) {
                            // Error. File is too big
                            // (too many lines, out of memory).
                            Toast.makeText(context, R.string.info_file_to_big,
                                    Toast.LENGTH_LONG).show();
                            return null;
                        }
                    }
                }
                if (linesArray.size() > 0) {
                    ret = linesArray.toArray(new String[linesArray.size()]);
                } else {
                    ret = new String[] {""};
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error while reading from file "
                        + file.getPath() + "." ,e);
                ret = null;
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    }
                    catch (IOException e) {
                        Log.e(LOG_TAG, "Error while closing file.", e);
                        ret = null;
                    }
                }
            }
        }
        return ret;
    }

    public static int isValidDump(String[] lines, boolean ignoreAsterisk) {
        ArrayList<Integer> knownSectors = new ArrayList<Integer>();
        int blocksSinceLastSectorHeader = 4;
        boolean is16BlockSector = false;
        if (lines == null || lines.length == 0) {
            // There are no lines.
            return 6;
        }
        for(String line : lines) {
            if ((!is16BlockSector && blocksSinceLastSectorHeader == 4)
                    || (is16BlockSector && blocksSinceLastSectorHeader == 16)) {
                // A sector header is expected.
                if (!line.matches("^\\+Sector: [0-9]{1,2}$")) {
                    // Not a valid sector length or not a valid sector header.
                    return 1;
                }
                int sector;
                try {
                    sector = Integer.parseInt(line.split(": ")[1]);
                } catch (Exception ex) {
                    // Not a valid sector header.
                    // Should not occur due to the previous check (regex).
                    return 1;
                }
                if (sector < 0 || sector > 39) {
                    // Sector out of range.
                    return 4;
                }
                if (knownSectors.contains(sector)) {
                    // Two times the same sector number (index).
                    // Maybe this is a file containing multiple dumps
                    // (the dump editor->save->append function was used).
                    return 5;
                }
                knownSectors.add(sector);
                is16BlockSector = (sector >= 32);
                blocksSinceLastSectorHeader = 0;
                continue;
            }
            if (line.startsWith("*") && ignoreAsterisk) {
                // Ignore line and move to the next sector.
                // (The line was a "No keys found or dead sector" message.)
                is16BlockSector = false;
                blocksSinceLastSectorHeader = 4;
                continue;
            }
            if (!line.matches("[0-9A-Fa-f-]+")) {
                // Not pure hex (or NO_DATA).
                return 2;
            }
            if (line.length() != 32) {
                // Not 32 chars per line.
                return 3;
            }
            blocksSinceLastSectorHeader++;
        }
        return 0;
    }

    public static SpannableString colorString(String data, int color) {
        SpannableString ret = new SpannableString(data);
        ret.setSpan(new ForegroundColorSpan(color),
                0, data.length(), 0);
        return ret;
    }

    public static boolean isValueBlock(String hexString) {
        byte[] b = Common.hexStringToByteArray(hexString);
        if (b.length == 16) {
            // Google some NXP info PDFs about MIFARE Classic to see how
            // Value Blocks are formatted.
            // For better reading (~ = invert operator):
            // if (b0=b8 and b0=~b4) and (b1=b9 and b9=~b5) ...
            // ... and (b12=b14 and b13=b15 and b12=~b13) then
            if (    (b[0] == b[8] && (byte)(b[0]^0xFF) == b[4]) &&
                    (b[1] == b[9] && (byte)(b[1]^0xFF) == b[5]) &&
                    (b[2] == b[10] && (byte)(b[2]^0xFF) == b[6]) &&
                    (b[3] == b[11] && (byte)(b[3]^0xFF) == b[7]) &&
                    (b[12] == b[14] && b[13] == b[15] &&
                            (byte)(b[12]^0xFF) == b[13])) {
                return true;
            }
        }
        return false;
    }
}
