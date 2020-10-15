package com.oneplay.android;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.oneplay.android.Bean.AliyunDNSBean;
import com.oneplay.android.Bean.CustomContentData;
import com.oneplay.android.Bean.MySharedPrefernces;
import com.oneplay.android.Common.GetHttpRequest;
import com.oneplay.android.Common.LogUtils;
import com.oneplay.android.Common.NetworkUtils;
import com.oneplay.android.Common.OnCallbackListener;
import com.oneplay.android.Common.StatusBarUtils;
import com.oneplay.android.Dialog.DialogConfirm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;


public class MainActivity extends Activity {

    private static final int REQUEST_CODE = 12;
    private String mAccountIp = "";
    private WebView mWebView;
    private Gson mGson = new Gson();
    private long mLastTime = 0L;
    private boolean mFlagOnce = true;
    private String mStrMainUrl = "";
    private Context mContext;
    private DialogConfirm mDialogConfirm;

    private RelativeLayout mBoxStartPage;
    private RelativeLayout mBoxCountTime;
    private TextView mTvCountTime;
    private ImageView mImgStartPage;
    private CountDownTimer mCountDownTimer;
    private long mIntCountTimeTotal = 4 * 1100L;

    private String mRootUrl = "";

    private ValueCallback<Uri> uploadMessage;
    private ValueCallback<Uri[]> uploadMessageAboveL;
    private final static int FILE_CHOOSER_RESULT_CODE = 10000;


    private PowerManager powerManager = null;
    private PowerManager.WakeLock wakeLock = null;

    private RelativeLayout mBoxMsg;
    private TextView mBtnRetry;
    private TextView mTvMsg;
    private boolean isCalling = false;
    private ProgressBar mProgressBar;
    private String mStrUrlSubstring = "";
    private RelativeLayout mLayout;
    private boolean isHttPDNS = true;
    private final String URL = "http://m.kfcs123.com/index?APP";
    private final String URL_JUMP = "http://m.kfcs123.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreen(true);
        setContentView(R.layout.activity_main);
        // 解决重新单击图标会重启应用问题。
        /*if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            finish();
            return;
        }*/
        // 如果不行再试试这个
        if (!isTaskRoot()) {
            finish();
            return;
        }
        setupLoading();
        mContext = this;
        mRootUrl = getResources().getString(R.string.root_path);
        mAccountIp = getResources().getString(R.string.account_id);
        setupCountTime();
        checkPermission();
        //QbSdk.initX5Environment(getApplicationContext(), null);
        initWakeLock();
        setupMessageBox();
        gotoWebViewDoStart();
        setupDialog();
        setupWebView();

    }


    //true隐藏，false显示
    /*private void hideStatusBar(boolean enable) {
        if (enable) {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN;
            getWindow().setAttributes(attrs);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }*/


    private void fullscreen(boolean enable) {
        //隐藏状态栏
        if (enable) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            getWindow().setAttributes(lp);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        } else {
            //显示状态栏
            WindowManager.LayoutParams lp = getWindow().getAttributes();

            lp.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);

            getWindow().setAttributes(lp);

            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        }


    }

    private void setupLoading() {
        mProgressBar = findViewById(R.id.LoadingView);

    }

    private void setupMessageBox() {
        mBoxMsg = findViewById(R.id.box_message);
        mBtnRetry = findViewById(R.id.btn_retry);
        mTvMsg = findViewById(R.id.tv_msg);

        mBtnRetry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onClick: "+mWebView.getUrl());
                if (NetworkUtils.isNetworkConnected(getApplicationContext())) {
                    if (TextUtils.isEmpty(mWebView.getUrl())) {
                        doOkHttpSecond();
                    } else {
                        mWebView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mWebView.loadUrl(mWebView.getUrl());
                            }
                        }, 200);
                        mBoxMsg.setVisibility(View.GONE);

                    }

                } else {
                    showToast("当前无网络");
                }
            }
        });
    }

    private Handler mHandler = new Handler();

    private void setupCountTime() {
        mBoxStartPage = findViewById(R.id.box_start_page);
        mBoxCountTime = findViewById(R.id.box_count_time);
        mImgStartPage = findViewById(R.id.img_start_page);
        mTvCountTime = findViewById(R.id.tv_count);

        mTvCountTime.setText(((mIntCountTimeTotal / 1000) - 1) + "");
        mCountDownTimer = new CountDownTimer(mIntCountTimeTotal, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long temp = (millisUntilFinished / 1000 - 1);
                if (temp <= 0) {
                    temp = 0;
                }
                long finalTemp = temp;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTvCountTime.setText("跳过 " + finalTemp + "s");
                        LogUtils.out(millisUntilFinished + "");
                    }
                });


            }

            @Override
            public void onFinish() {
                isCalling = true;
                gotoWebView();
                fullscreen(false);

            }
        };
        mCountDownTimer.start();

        mBoxCountTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCountDownTimer != null) {
                    mCountDownTimer.cancel();
                }
                isCalling = true;
                gotoWebView();
                fullscreen(false);
            }
        });
    }
    boolean isFirst = true;
    private void setupWebView() {
        mWebView = findViewById(R.id.webwiew);
        mWebView.setHorizontalScrollBarEnabled(false);
        mWebView.setVerticalScrollBarEnabled(false);
        WebSettings webSetting = mWebView.getSettings();
        webSetting.setJavaScriptEnabled(true);
        webSetting.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSetting.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        webSetting.setLoadWithOverviewMode(true);
        webSetting.setSupportZoom(false);
        webSetting.setAllowFileAccess(true);
        webSetting.setJavaScriptCanOpenWindowsAutomatically(true);
        webSetting.setLoadsImagesAutomatically(true);
        webSetting.setDefaultTextEncodingName("utf-8");
        webSetting.setUseWideViewPort(true);
        webSetting.setDatabaseEnabled(true);
        webSetting.setDomStorageEnabled(true);
        webSetting.setBuiltInZoomControls(true);
        // TODO: 2019-10-22 待測試
        // 清缓存和记录，缓存引起的白屏
//        mWebView.clearCache(true);
//        mWebView.clearHistory();

//        mWebView.requestFocus();
//        WebSettings webSettings = mWebView.getSettings();
//        webSettings.setDatabaseEnabled(true);
//// 缓存白屏
//        String appCachePath = getApplicationContext().getCacheDir()
//                .getAbsolutePath() + "/webcache";
//// 设置 Application Caches 缓存目录
//        webSettings.setAppCachePath(appCachePath);
//        webSettings.setDatabasePath(appCachePath);
//        webSettings.setDomStorageEnabled(true);

        webSetting.setAppCacheEnabled(true);
        mWebView.addJavascriptInterface(new WebAppInterface(), "Android");
        mWebView.getSettings().setDomStorageEnabled(true);
        //  这个是锅。
        //  这个是锅。
        //  这个是锅。
        //  这个是锅。
        //  会导致无法打开新的窗口，这里需要注意。11
        //  webSetting.setSupportMultipleWindows(true);
        webSetting.setGeolocationEnabled(true);
        webSetting.setAppCacheMaxSize(Long.MAX_VALUE);
        webSetting.setPluginState(WebSettings.PluginState.ON_DEMAND);
        mWebView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                WebView.HitTestResult hitTestResult = mWebView.getHitTestResult();

                switch (hitTestResult.getType()) {
                    case WebView.HitTestResult.IMAGE_TYPE:
                        if (mDialogConfirm != null) {
                            mDialogConfirm.show();
                        }
                        break;
                }


                return false;
            }
        });
        mWebView.setWebChromeClient(new WebChromeClient() {

            // For Android < 3.0
            public void openFileChooser(ValueCallback<Uri> valueCallback) {
                uploadMessage = valueCallback;
                openImageChooserActivity();
            }

            // For Android  >= 3.0
            public void openFileChooser(ValueCallback valueCallback, String acceptType) {
                uploadMessage = valueCallback;
                openImageChooserActivity();
            }

            //For Android  >= 4.1
            public void openFileChooser(ValueCallback<Uri> valueCallback, String acceptType, String capture) {
                uploadMessage = valueCallback;
                openImageChooserActivity();
            }

            // For Android >= 5.0
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                uploadMessageAboveL = filePathCallback;
                openImageChooserActivity();
                return true;
            }


        });


        mWebView.setWebViewClient(new WebViewClient() {

            /*@Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return super.shouldOverrideUrlLoading(view, request);
            }*/

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                LogUtils.out("shouldOverrideUrlLoading   url = " + mWebView.getUrl());
                Log.d(TAG, "shouldOverrideUrlLoading: "+url);

                // 非空判断。 // 判断是不是被劫持到淘宝，如果是就让界面回退，然后浏览器不做处理。
                if (!TextUtils.isEmpty(url) && url.contains("tbopen://")) {
                    webviewGoBack();
                    mWebView.goBack();
                    return false;
                }
                //返回false，意味着请求过程里，不管有多少次的跳转请求（即新的请求地址），均交给webView自己处理，这也是此方法的默认处理
                //返回true，说明你自己想根据url，做新的跳转，比如在判断url符合条件的情况下，我想让webView加载http://ask.csdn.net/questions/178242
                if (url.contains("mqqwpa://") || url.contains("xapp-target=browser")) {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                    return true;
                }
//                mWebView.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//
//                    }
//                }, 200);
                mWebView.loadUrl(url);
                return false;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                super.onReceivedSslError(view, handler, error);
                handler.proceed();  //接受所有证书

            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                LogUtils.out(" onPageStarted = " + mWebView.getUrl());


                if(!mWebView.getUrl().contains(MySharedPrefernces.geturl(getApplicationContext())+"letter?ID=")
                        &&!mWebView.getUrl().contains(MySharedPrefernces.geturl(getApplicationContext())+"chat/user/")){
                    getUrl(mWebView.getUrl());
                }




                //mWebView.getUrl()+"/chat/user/"+customContentData.id+"?n="+customContentData.n;


            }


            @Override
            public void onPageFinished(WebView view, String url) {

                String theUrl = mWebView.getUrl();
                if (!TextUtils.isEmpty(mStrMainUrl) && !TextUtils.isEmpty(theUrl)) {
                    if (theUrl.startsWith(mStrMainUrl)) {
                        mWebView.setVisibility(View.VISIBLE);
                        mProgressBar.setVisibility(View.GONE);
                        //hideStatusBar(false);
                        //fullscreen(false);
                    }
                }

                /*
                    判断一次
                    获取到当前的根目录域名
                */
                if (mFlagOnce) {
                    mFlagOnce = false;
                    mStrMainUrl = mWebView.getUrl();
                }
                Log.d(TAG, "onPageFinished: "+mWebView.getUrl());

            }


            @Override
            public void onReceivedError(WebView webView, int errorCode, String s, String s1) {
                super.onReceivedError(webView, errorCode, s, s1);
                // 无网络问题
                // 跳出提示框
                /*if (errorCode == -1) {
                    mTvMsg.setText(R.string.error_msg_not_network);
                    mBoxMsg.setVisibility(View.VISIBLE);
                } else {
                    mTvMsg.setText(R.string.error_msg_other);
                    mBoxMsg.setVisibility(View.VISIBLE);
                }
                mProgressBar.setVisibility(View.GONE);*/
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (webView != null && webView.getUrl() != null && webView.getUrl().equals("tbopen")) {
                        shouldOverrideUrlLoading(webView, webView.getUrl());
                    }
                }
            }

            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                // 无网络问题
                // 跳出提示框
                /*if (error.getErrorCode() == -1) {
                    mTvMsg.setText(R.string.error_msg_not_network);
                    mBoxMsg.setVisibility(View.VISIBLE);
                } else {
                    mTvMsg.setText(R.string.error_msg_other);
                    mBoxMsg.setVisibility(View.VISIBLE);
                }
                mProgressBar.setVisibility(View.GONE);*/
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (request.getUrl().getScheme().equals("tbopen")) {
                        shouldOverrideUrlLoading(view, request.getUrl().getPath());
                    }
                }
            }


            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                // 无网络问题
                // 跳出提示框
                /*if (errorResponse.getStatusCode() == -1) {
                    mTvMsg.setText(R.string.error_msg_not_network);
                    mBoxMsg.setVisibility(View.VISIBLE);
                } else {
                    mTvMsg.setText(R.string.error_msg_other);
                    mBoxMsg.setVisibility(View.VISIBLE);
                }
                mProgressBar.setVisibility(View.GONE);*/
                // 如果是tbopen那就手动跳转到重定向首页
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    if (request.getUrl().getScheme().equals("tbopen")) {
                        shouldOverrideUrlLoading(view, request.getUrl().getPath());
                    }
                }
            }



            /*@Override
            public void onReceivedError(WebView webView, WebResourceRequest webResourceRequest, WebResourceError webResourceError) {
                super.onReceivedError(webView, webResourceRequest, webResourceError);
                // 无网络问题
                // 跳出提示框
                if (webResourceError.getErrorCode() == -1) {
                    mTvMsg.setText(R.string.error_msg_not_network);
                    mBoxMsg.setVisibility(View.VISIBLE);
                } else {
                    mTvMsg.setText(R.string.error_msg_other);
                    mBoxMsg.setVisibility(View.VISIBLE);
                }
            }*/

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return super.shouldInterceptRequest(view, url);
            }
        });
        //mWebView.loadUrl("http://52.175.50.221");

//        mWebView.loadUrl(URL);

    }

    private void webviewGoBack() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        }
    }

    private void setupDialog() {
        mDialogConfirm = new DialogConfirm(mContext);
        mDialogConfirm.setListener(new DialogConfirm.OnClickListener() {
            @Override
            public void onSave() {
                WebView.HitTestResult hitTestResult = mWebView.getHitTestResult();
                Log.d(TAG, "onSave: "+"");
                // 下载图片
                new TaskDownloadImage().execute(hitTestResult.getExtra());
            }


            @Override
            public void onBrowse() {
                Log.d(TAG, "onBrowse: ");
                WebView.HitTestResult hitTestResult = mWebView.getHitTestResult();
                Intent intent = new Intent(mContext, BrowseImageActivity.class);
                intent.putExtra("url", hitTestResult.getExtra());
                startActivity(intent);
            }
        });
    }

    @SuppressLint("InvalidWakeLockTag")
    private void initWakeLock() {
        powerManager = (PowerManager) this.getSystemService(this.POWER_SERVICE);
        wakeLock = this.powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "My Lock");
    }


    private void openImageChooserActivity() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Image Chooser"), FILE_CHOOSER_RESULT_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (null == uploadMessage && null == uploadMessageAboveL) return;
            Uri result = data == null || resultCode != RESULT_OK ? null : data.getData();
            if (uploadMessageAboveL != null) {
                onActivityResultAboveL(requestCode, resultCode, data);
            } else if (uploadMessage != null) {
                uploadMessage.onReceiveValue(result);
                uploadMessage = null;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void onActivityResultAboveL(int requestCode, int resultCode, Intent intent) {
        if (requestCode != FILE_CHOOSER_RESULT_CODE || uploadMessageAboveL == null)
            return;
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (intent != null) {
                String dataString = intent.getDataString();
                ClipData clipData = intent.getClipData();
                if (clipData != null) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        results[i] = item.getUri();
                    }
                }
                if (dataString != null)
                    results = new Uri[]{Uri.parse(dataString)};
            }
        }
        uploadMessageAboveL.onReceiveValue(results);
        uploadMessageAboveL = null;
    }

    private void gotoWebViewDoStart() {
        boolean networkConnected = NetworkUtils.isNetworkConnected(getApplicationContext());
        if (networkConnected) {
            doOkHttpSecond();
            isCalling = true;
        } else {
            mBoxMsg.setVisibility(View.VISIBLE);
        }
    }

    private void gotoWebView() {
        boolean networkConnected = NetworkUtils.isNetworkConnected(getApplicationContext());

        // 添加动画 过度不生硬
        ObjectAnimator animatorAlpha = ObjectAnimator.ofFloat(mBoxStartPage, "alpha", 1F, 0F);
        //ObjectAnimator animatorScaleY = ObjectAnimator.ofFloat(mBoxStartPage, "scaleY", 1F, 0F);
        //ObjectAnimator animatorScaleX = ObjectAnimator.ofFloat(mBoxStartPage, "scaleX", 1F, 0F);
        AnimatorSet set = new AnimatorSet();
        set.setDuration(500);
        set.playTogether(
                animatorAlpha/*,
                animatorScaleY,
                animatorScaleX*/
        );
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mBoxStartPage.setVisibility(View.GONE);


                // 销毁销毁启动图，避免内存溢出
                if (mImgStartPage != null) {
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) mImgStartPage.getDrawable();
                    if (bitmapDrawable != null) {
                        Bitmap bitmap = bitmapDrawable.getBitmap();
                        if (bitmap != null) {
                            bitmap.recycle();
                        }
                    }
                    // 销毁销毁启动图，避免内存溢出
                    mImgStartPage.setImageDrawable(null);
                }
            }
        });
        set.start();


        if (networkConnected) {
            if (!isCalling) {
                doOkHttpSecond();
            }
        } else {
//            mBoxMsg.setVisibility(View.VISIBLE);
            //hideStatusBar(false);
            fullscreen(false);
        }
    }


    private void doOkHttpSecond() {


        try {
            // 开启转圈
            mProgressBar.setVisibility(View.VISIBLE);

            //1，获取 host
            URL url = new URL(mRootUrl);
            String host = url.getHost();

            LogUtils.out(mRootUrl);

            GetHttpRequest httpRequest = new GetHttpRequest();
            httpRequest.setUrl("http://203.107.1.33/" + mAccountIp + "/d?host=" + host);
            httpRequest.setOnCallbackListener(new OnCallbackListener() {
                @Override
                public void onSuccess(String data) {
                    Log.d(TAG, "onSuccess: "+data);

                    AliyunDNSBean dnsBean = mGson.fromJson(data, AliyunDNSBean.class);
                    LogUtils.out(mGson.toJson(dnsBean));
                    List<String> ips = dnsBean.getIps();
                    if (ips == null || ips.size() == 0) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mBoxMsg.setVisibility(View.VISIBLE);
                                mProgressBar.setVisibility(View.GONE);
                            }
                        });
                        return;
                    }


                    // 3 ， 获得到ip后，重新替换host为ip地址
                    // 4 ， 执行请求
                    String finalUrl = mRootUrl.replace(host, ips.get(0));

                    Log.d(TAG, "onSuccess: "+finalUrl);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                        }
                    });

                    GetHttpRequest httpRequest = new GetHttpRequest();
                    httpRequest.setUrl(finalUrl);
                    httpRequest.setOnCallbackListener(new OnCallbackListener() {
                        @Override
                        public void onSuccess(String str) throws UnsupportedEncodingException {

                            LogUtils.out(str);
                            byte[] data = Base64.decode(str, Base64.DEFAULT);
                            String loadUrl = new String(data, "utf-8");

                            LogUtils.out(loadUrl);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    mWebView.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            mWebView.loadUrl(loadUrl);
                                        }
                                    }, 200);

                                    /*mStrMainUrl = "http://2fhc.com";
                                    mWebView.loadUrl("http://2fhc.com");*/

                                    if (loadUrl.contains("?")) {
                                        mStrUrlSubstring = loadUrl.substring(0, loadUrl.indexOf("?"));
                                    } else {
                                        mStrUrlSubstring = loadUrl;
                                    }
                                    mStrMainUrl = mStrUrlSubstring;
                                    Log.d(TAG, "run: "+mStrMainUrl);
                                    mBoxMsg.setVisibility(View.GONE);

                                }
                            });
                        }

                        @Override
                        public void onError(String msg) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mBoxMsg.setVisibility(View.VISIBLE);
                                }
                            });

                        }
                    });
                    httpRequest.start();
                }

                @Override
                public void onError(String msg) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mBoxMsg.setVisibility(View.VISIBLE);
                        }
                    });
                }
            });
            httpRequest.start();
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    @Override
    public void onBackPressed() {


        // 如果还在加载页，则不执行退出
        if (mBoxStartPage != null && mBoxStartPage.getVisibility() == View.VISIBLE) {
            return;
        }

        String webViewUrl = mWebView.getUrl();
        if (webViewUrl == null) {
            finish();
        } else {
            LogUtils.out(webViewUrl);
            if (!TextUtils.isEmpty(mStrUrlSubstring) && !TextUtils.isEmpty(webViewUrl) && webViewUrl.startsWith(mStrUrlSubstring)) {
                showToast("再次返回退出应用");
                if ((System.currentTimeMillis() - mLastTime) <= 3000) {
                    finish();
                }
                mLastTime = System.currentTimeMillis();
            } else {
                if (mWebView.canGoBack()) {
                    mWebView.goBack();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        }

    }


    public class TaskDownloadImage extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... strings) {
            String urlPath = strings[0];

            URL url = null;
            HttpURLConnection connection = null;
            Bitmap bitmap = null;
            FileOutputStream fileOutputStream = null;
            InputStream inputStream = null;
            try {
                url = new URL(urlPath);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(6000);
                connection.setDoInput(true);
                connection.setUseCaches(true);
                inputStream = connection.getInputStream();
                bitmap = BitmapFactory.decodeStream(inputStream);
                String fileName = UUID.randomUUID().toString() + ".webp";
                File file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera/" + fileName);

                //文件夹不存在，则创建它
                if (!file.getParentFile().getAbsoluteFile().exists()) {
                    file.mkdir();
                }

                fileOutputStream = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.WEBP, 20, fileOutputStream);
                fileOutputStream.close();


                // 最后通知图库更新
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath()));
                sendBroadcast(intent);


            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (fileOutputStream != null) {
                        fileOutputStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }


            return "下载完成";
        }

        @Override
        protected void onPostExecute(String s) {
            showToast(s);
        }
    }


    private void checkPermission() {
        String[] permissions = new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.MANAGE_DOCUMENTS

        };


        boolean hasPermissions = false;
        for (int i = 0; i < permissions.length; i++) {
            int hasWriteStoragePermission = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            LogUtils.out(permissions[i] + "  =  " + hasWriteStoragePermission);
            if (hasWriteStoragePermission != PackageManager.PERMISSION_GRANTED) {
                hasPermissions = false;
                break;
            }
        }
        if (!hasPermissions) {
            //没有权限，向用户请求权限
            ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_CODE);
        }


        // 兼容小米的权限申请。
        /*if (Build.VERSION.SDK_INT >= 23) {
            int checkLocalPhonePermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (checkLocalPhonePermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        permissions, REQUEST_CODE);
                return;
            }
            //适配小米机型
            AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int checkOp = appOpsManager.checkOp(AppOpsManager.OPSTR_FINE_LOCATION, android.os.Process.myUid(), getPackageName());
            if (checkOp == AppOpsManager.MODE_IGNORED) {
                ActivityCompat.requestPermissions(this,
                        permissions, REQUEST_CODE);
                return;
            }
        }*/


    }


    @Override
    protected void onResume() {
        super.onResume();
        if (wakeLock != null) {
            wakeLock.acquire();
        }


        setCustomContent();

    }

    @Override
    protected void onPause() {
        super.onPause();
        // 解决isHeld错误问题。
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {

                String[] thePermissions = new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.MANAGE_DOCUMENTS

                };
                boolean hasPermissions = false;
                for (int i = 0; i < thePermissions.length; i++) {
                    int hasWriteStoragePermission = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    //LogUtils.out(permissions[i] + "  =  " + hasWriteStoragePermission);
                    if (hasWriteStoragePermission != PackageManager.PERMISSION_GRANTED) {
                        hasPermissions = false;
                        break;
                    }
                }
                if (!hasPermissions) {
                    //没有权限，向用户请求权限
                    new AlertDialog.Builder(mContext)
                            .setMessage("为了给您带来更好的体验，请授权我们。")
                            .setPositiveButton("OK", (dialog1, which) ->
                                    ActivityCompat.requestPermissions(MainActivity.this,
                                            thePermissions,
                                            REQUEST_CODE))
                            .setNegativeButton("Cancel", null)
                            .create()
                            .show();
                    return;
                }
            }
            // TODO: 2019/6/15 去适配MIUI
//            if (Build.MANUFACTURER.equals("Xiaomi")) {
//                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
//                    // 弹出对话框，让用户去设置权限
//                    AlertDialog dialog = new AlertDialog.Builder(this)
//                            .setMessage("我们需要您同意我们获取读写文件权限")
//                            .setPositiveButton("前往授权", new DialogInterface.OnClickListener() {
//                                @Override
//                                public void onClick(DialogInterface dialog, int which) {
//                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//                                    // 根据包名打开对应的设置界面
//                                    intent.setData(Uri.parse("package:" + getPackageName()));
//                                    startActivity(intent);
//                                }
//                            })
//                            .setNegativeButton("取消授权", null).create();
//                    dialog.show();
//                }
//            }

        }
    }


    public void showToast(String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static final String TAG = "MainActivity";

    public class WebAppInterface {


        @JavascriptInterface
        public String getToken() {
            return MySharedPrefernces.getToken(getApplicationContext());

        }
    }


    private void setCustomContent() {
//        MySharedPrefernces.saveUrl(getApplicationContext(),"");


        String customContent = MySharedPrefernces.getCustomcontent(getApplicationContext());

        if (!customContent.equals("")) {
            String url = "";
            CustomContentData customContentData = new Gson().fromJson(customContent,CustomContentData.class);
            if (customContentData!=null){
                switch (customContentData.page){
                    //privateLetter（私信页面） notification（消息）
                    case "privateLetter":
                        ///chat/user/190
//                       url = URL_JUMP+"/chat/user/"+customContentData.id+"?n="+customContentData.n;
                        url = MySharedPrefernces.geturl(getApplicationContext())+"chat/user/"+customContentData.id+"?n="+customContentData.n +MySharedPrefernces.getKEY(getApplicationContext());
                        mWebView.loadUrl(url);
                        MySharedPrefernces.clearSharedPrefernces(getApplicationContext());

                        break;
                    case "notification":
                        ///letter?ID=190
//                        url = URL_JUMP+"/letter?ID="+customContentData.id;
                        url =MySharedPrefernces.geturl(getApplicationContext())+"letter?ID="+customContentData.id+MySharedPrefernces.getKEY(getApplicationContext());
                        mWebView.loadUrl(url);
                        MySharedPrefernces.clearSharedPrefernces(getApplicationContext());

                        break;
                }
            }
        }
    }

    private void getUrl(String mRootUrl){
        if(MySharedPrefernces.geturl(getApplicationContext()).equals("")){
            if (mRootUrl.contains("index?")){
                String s[] = mRootUrl.split("index\\?");
                if (s[0]!=null){
                    MySharedPrefernces.saveUrl(getApplicationContext(),s[0]);

                }
                if (s[1]!=null){
                    MySharedPrefernces.saveKEY(getApplicationContext(),"&"+s[1]);

                }


            }else if(mRootUrl.contains("?")) {
                String s[] = mRootUrl.split("\\?");
                if (s[0]!=null){
                    MySharedPrefernces.saveUrl(getApplicationContext(),s[0]);

                }
                if (s[1]!=null){
                    MySharedPrefernces.saveKEY(getApplicationContext(),"&"+s[1]);

                }


            }
        }else {
            if(!mWebView.getUrl().contains(MySharedPrefernces.geturl(getApplicationContext())+"letter?ID=")
                    &&!mWebView.getUrl().contains(MySharedPrefernces.geturl(getApplicationContext())+"chat/user/")){
                Log.d(TAG, "getUrl: "+"1");
                return;

            }
        }



    }

}




