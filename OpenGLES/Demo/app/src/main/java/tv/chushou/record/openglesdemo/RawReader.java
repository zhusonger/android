package tv.chushou.record.openglesdemo;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by zhusong on 18/3/26.
 */

public class RawReader {

    public static final String readTextFromRaw(Context context, int resId) {
        if (null == context) {
            return null;
        }

        InputStream is = context.getResources().openRawResource(resId);
        BufferedReader br = new BufferedReader(new InputStreamReader(is));

        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            is.close();
            is = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            br.close();
            br = null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return sb.toString();
    }
}