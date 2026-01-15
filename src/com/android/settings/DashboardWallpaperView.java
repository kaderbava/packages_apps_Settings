/*
 * Copyright (C) 2024-2025 Project Infinity X
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.Manifest;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.IOException;

public class DashboardWallpaperView extends AppCompatImageView {

    private static final String TAG = "DashboardWallpaperView";
    private static final String PREFS_NAME = "DashboardWallpaperViewPrefs";
    private static final String KEY_BLUR_RADIUS = "blurRadius";
    private static final String KEY_DIM_LEVEL = "dimLevel";
    private static final String KEY_TRANSPARENCY_STATE = "transparencyState";
    private static final String KEY_CUSTOM_IMAGE_URI = "customImageUri";

    private Context mContext;
    private float mBlurRadius = 0f;
    private float mDimLevel = 0f;
    private boolean mIsTransparencyEnabled = false;
    private boolean mIsCustomImageEnabled = false;
    private boolean mIsLaunchersRegistered = false;

    private ActivityResultLauncher<Intent> mPickImageLauncher;
    private ActivityResultLauncher<String> mRequestPermissionLauncher;
    private LifecycleEventObserver mLifecycleObserver;

    public DashboardWallpaperView(Context context) {
        this(context, null);
    }

    public DashboardWallpaperView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DashboardWallpaperView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        mContext = context;
        setScaleType(ScaleType.CENTER_CROP);
        loadSettings();
        setOnClickListener(v -> openAboutDeviceFragment());
        setOnLongClickListener(v -> {
            showSettingsDialog();
            return true;
        });

        if (context instanceof FragmentActivity) {
            setupLifecycleObserver((FragmentActivity) context);
        } else {
            Log.w(TAG, "Context is not a FragmentActivity - wallpaper features disabled");
            fetchAndSetWallpaper();
        }
    }

    private void setupLifecycleObserver(FragmentActivity activity) {
        mLifecycleObserver = new LifecycleEventObserver() {
            @Override
            public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
                if (event == Lifecycle.Event.ON_CREATE && !mIsLaunchersRegistered) {
                    registerLaunchers(activity);
                    mIsLaunchersRegistered = true;
                    fetchAndSetWallpaper();
                } else if (event == Lifecycle.Event.ON_DESTROY) {
                    activity.getLifecycle().removeObserver(this);
                }
            }
        };

        if (activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.INITIALIZED)) {
            activity.getLifecycle().addObserver(mLifecycleObserver);
        }
    }

    private void registerLaunchers(FragmentActivity activity) {
        mPickImageLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        handleSelectedImage(selectedImageUri);
                    }
                }
            }
        );

        mRequestPermissionLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openGallery();
                } else {
                    Toast.makeText(mContext, "Permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        );
    }

    private void openAboutDeviceFragment() {
        if (mContext instanceof FragmentActivity) {
            FragmentActivity activity = (FragmentActivity) mContext;
            FragmentManager fragmentManager = activity.getSupportFragmentManager();
            
            Fragment aboutFragment = new com.android.settings.deviceinfo.aboutphone.MyDeviceInfoFragment();
            
            fragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.content_frame, aboutFragment)
                .addToBackStack(null)
                .commit();
        }
    }

    private void openGallery() {
        if (!mIsLaunchersRegistered) return;
        Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.setType("image/*");
        mPickImageLauncher.launch(intent);
    }

    private void handleSelectedImage(Uri selectedImageUri) {
        try {
            mContext.getContentResolver().takePersistableUriPermission(
                selectedImageUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            saveCustomImageUri(selectedImageUri);
            mIsCustomImageEnabled = true;
            saveSettings();
            fetchAndSetWallpaper();
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to take persistable URI permission", e);
        }
    }

    private void applyDim() {
        setColorFilter(Color.argb((int) (mDimLevel * 2.55), 0, 0, 0));
    }

    private void applyTransparency() {
        setAlpha(mIsTransparencyEnabled ? 0.00001f : 1f);
    }

    private void applyBlur() {
        setRenderEffect(RenderEffect.createBlurEffect(mBlurRadius, mBlurRadius, Shader.TileMode.CLAMP));
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(mContext).inflate(R.layout.wallpaper_dashboard, null);

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(mContext, R.style.CustomBottomSheetDialog);
        bottomSheetDialog.setContentView(dialogView);

        Window window = bottomSheetDialog.getWindow();
        if (window != null) {
            window.setDimAmount(0.6f);
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams params = window.getAttributes();
            params.y = 30;
            window.setAttributes(params);
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, 
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.setLayout((int) (mContext.getResources().getDisplayMetrics().widthPixels * 0.85), 
                WindowManager.LayoutParams.WRAP_CONTENT);

            ViewCompat.setOnApplyWindowInsetsListener(dialogView, (v, insets) -> {
                Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom);
                return insets;
            });
        }

        if (dialogView != null && dialogView.getParent() instanceof View) {
            View parent = (View) dialogView.getParent();
            parent.setBackgroundColor(Color.TRANSPARENT);
        }

        Button importImageButton = dialogView.findViewById(R.id.importImageButton);
        importImageButton.setOnClickListener(v -> {
            checkAndRequestPermissions();
            bottomSheetDialog.dismiss();
        });

        MaterialSwitch customImageSwitch = dialogView.findViewById(R.id.customImageSwitch);
        customImageSwitch.setChecked(mIsCustomImageEnabled);
        importImageButton.setVisibility(mIsCustomImageEnabled ? View.VISIBLE : View.GONE);
        customImageSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mIsCustomImageEnabled = isChecked;
            saveSettings();
            importImageButton.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            fetchAndSetWallpaper();
        });

        SeekBar blurSeekBar = dialogView.findViewById(R.id.blurSeekBar);
        blurSeekBar.setProgress((int) mBlurRadius);
        blurSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mBlurRadius = progress;
                applyBlur();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });

        SeekBar dimSeekBar = dialogView.findViewById(R.id.dimSeekBar);
        dimSeekBar.setProgress((int) mDimLevel);
        dimSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mDimLevel = progress;
                applyDim();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });

        MaterialSwitch transparencySwitch = dialogView.findViewById(R.id.transparencySwitch);
        transparencySwitch.setChecked(mIsTransparencyEnabled);
        transparencySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mIsTransparencyEnabled = isChecked;
            applyTransparency();
            saveSettings();
        });

        bottomSheetDialog.setOnShowListener(dialog -> {
            ViewGroup bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setExpandedOffset(20);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        bottomSheetDialog.show();
    }

    private void checkAndRequestPermissions() {
        if (!mIsLaunchersRegistered) return;
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
            mRequestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            openGallery();
        }
    }

    private void fetchAndSetWallpaper() {
        if (mIsCustomImageEnabled) {
            Uri customImageUri = getCustomImageUri();
            if (customImageUri != null) {
                loadImageFromUri(customImageUri);
                return;
            }
        }
        fetchDefaultWallpaper();
    }

    private void loadImageFromUri(Uri uri) {
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(mContext.getContentResolver(), uri);
            Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });

            if (drawable instanceof AnimatedImageDrawable) {
                setImageDrawable(drawable);
                ((AnimatedImageDrawable) drawable).start();
            } else {
                setImageBitmap(ImageDecoder.decodeBitmap(source));
            }

            applyBlur();
            applyDim();
            applyTransparency();
        } catch (IOException e) {
            Log.e(TAG, "Failed to load image from URI", e);
            mIsCustomImageEnabled = false;
            saveSettings();
            fetchDefaultWallpaper();
        }
    }

    private void fetchDefaultWallpaper() {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
        Drawable wallpaperDrawable = wallpaperManager.getDrawable();
        if (wallpaperDrawable != null) {
            Bitmap wallpaperBitmap = drawableToBitmap(wallpaperDrawable);
            if (wallpaperBitmap != null) {
                setImageBitmap(wallpaperBitmap);
                applyBlur();
                applyDim();
                applyTransparency();
            }
        }
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(
            drawable.getIntrinsicWidth(),
            drawable.getIntrinsicHeight(),
            Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private void loadSettings() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mBlurRadius = prefs.getFloat(KEY_BLUR_RADIUS, 0f);
        mDimLevel = prefs.getFloat(KEY_DIM_LEVEL, 0f);
        mIsTransparencyEnabled = prefs.getBoolean(KEY_TRANSPARENCY_STATE, false);
        mIsCustomImageEnabled = prefs.getBoolean("isCustomImageEnabled", false);
        applyBlur();
        applyDim();
        applyTransparency();
    }

    private void saveCustomImageUri(Uri uri) {
        SharedPreferences.Editor editor = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_CUSTOM_IMAGE_URI, uri.toString());
        editor.apply();
    }

    private Uri getCustomImageUri() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String uriString = prefs.getString(KEY_CUSTOM_IMAGE_URI, null);
        return uriString != null ? Uri.parse(uriString) : null;
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putFloat(KEY_BLUR_RADIUS, mBlurRadius);
        editor.putFloat(KEY_DIM_LEVEL, mDimLevel);
        editor.putBoolean(KEY_TRANSPARENCY_STATE, mIsTransparencyEnabled);
        editor.putBoolean("isCustomImageEnabled", mIsCustomImageEnabled);
        editor.apply();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mContext instanceof FragmentActivity && mLifecycleObserver != null) {
            ((FragmentActivity) mContext).getLifecycle().removeObserver(mLifecycleObserver);
        }
    }
}
