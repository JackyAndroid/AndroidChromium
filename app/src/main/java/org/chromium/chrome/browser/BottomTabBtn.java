package org.chromium.chrome.browser;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.fumujidi.chromiumforandroid.R;

/**
 * Created by Staray-Xu on 2016/4/15.
 * 底部状态栏Tab按钮
 */
public class BottomTabBtn extends FrameLayout {
    private TextView tabCountTv;

    public BottomTabBtn(Context context) {
        super(context, null);
        if (!isInEditMode()) {
            init(context);
        }
    }

    public BottomTabBtn(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            init(context);
        }
    }

    private void init(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_tab_btn, this);
        tabCountTv = (TextView) view.findViewById(R.id.bottom_btn_tab_num_tv);
    }

    public void setTabCountTv(String tabCount) {
        if (null == tabCount) {
            return;
        }
        tabCountTv.setText(tabCount);
    }
}
