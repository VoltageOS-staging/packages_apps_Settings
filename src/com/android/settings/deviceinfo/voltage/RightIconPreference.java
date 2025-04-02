package com.android.settings.deviceinfo.voltage;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import com.android.settings.R;

public class RightIconPreference extends Preference {

    public RightIconPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Set the custom layout here
        setLayoutResource(R.layout.preference_right_icon);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        
        // Set icon to your drawable (retain your controller logic)
        ImageView icon = (ImageView) holder.findViewById(android.R.id.icon);
        if (icon != null) {
            icon.setImageDrawable(getIcon());
            icon.setVisibility(View.VISIBLE);
        }
    }
}
