package com.vaenow.appupdate.android;

import android.app.Activity;
import android.Manifest;
import android.os.Build;
import android.net.Uri;
import android.provider.Settings;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.BuildHelper;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * Created by LuoWen on 2015/10/27.
 */
public class CheckAppUpdate extends CordovaPlugin {
    public static final String TAG = "CheckAppUpdate";

    private UpdateManager updateManager = null;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE };
    private static final int INSTALL_PERMISSION_REQUEST_CODE = 0;
    private static final int UNKNOWN_SOURCES_PERMISSION_REQUEST_CODE = 1;
    private static final int OTHER_PERMISSIONS_REQUEST_CODE = 2;

    // Other necessary permissions for this plugin.
    private static String[] OTHER_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {

        getUpdateManager(args, callbackContext);
        this.updateManager.updateUrl=args.getString(0);
        if (action.equals("checkAppUpdate")) {
            verifyStoragePermissions();
            this.updateManager.checkUpdate();
            return true;
        }
        else if(action.equals("install")){
            if (verifyInstallPermission() && verifyOtherPermissions()){
                this.updateManager.emitNoticeDialogOnClick1();
                return true;
            }
        }
        callbackContext.error(Utils.makeJSON(Constants.NO_SUCH_METHOD, "no such method: " + action));
        return false;
    }

    public void startInstall(){
        this.updateManager.emitNoticeDialogOnClick1();
    }

    // Prompt user for all other permissions if we don't already have them all.
    public boolean verifyOtherPermissions() {
        boolean hasOtherPermissions = true;
        for (String permission:OTHER_PERMISSIONS)
            hasOtherPermissions = hasOtherPermissions && cordova.hasPermission(permission);

        if (!hasOtherPermissions) {
            cordova.requestPermissions(this, OTHER_PERMISSIONS_REQUEST_CODE, OTHER_PERMISSIONS);
            return false;
        }

        return true;
    }
    
    // React to user's response to our request for install permission.
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == INSTALL_PERMISSION_REQUEST_CODE) {
            if (!cordova.getActivity().getPackageManager().canRequestPackageInstalls()) {
                this.updateManager.permissionDenied("Permission Denied: " + Manifest.permission.REQUEST_INSTALL_PACKAGES);
                return;
            }

            if (verifyOtherPermissions())
                startInstall(); 
        }
        else if (requestCode == UNKNOWN_SOURCES_PERMISSION_REQUEST_CODE) {
            try {
                if (Settings.Secure.getInt(cordova.getActivity().getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS) != 1) {
                    this.updateManager.permissionDenied("Permission Denied: " + Settings.Secure.INSTALL_NON_MARKET_APPS);
                    return;
                }
            }
            catch (Settings.SettingNotFoundException e) {}

            if (verifyOtherPermissions())
                startInstall(); 
        }
    }

    public UpdateManager getUpdateManager(JSONArray args, CallbackContext callbackContext)
            throws JSONException {

        if (this.updateManager == null) {
            this.updateManager = new UpdateManager(this.cordova.getActivity(), this.cordova);
        }

        return this.updateManager.options(args, callbackContext);
    }

    public void verifyStoragePermissions() {
        // Check if we have write permission
        // and if we don't prompt the user
        if (!cordova.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            cordova.requestPermissions(this, REQUEST_EXTERNAL_STORAGE, PERMISSIONS_STORAGE);
        }
    }
        // Prompt user for install permission if we don't already have it.
    public boolean verifyInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!cordova.getActivity().getPackageManager().canRequestPackageInstalls()) {
                String applicationId = (String) BuildHelper.getBuildConfigValue(cordova.getActivity(), "APPLICATION_ID");
                Uri packageUri = Uri.parse("package:" + applicationId);
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setData(packageUri);
                cordova.setActivityResultCallback(this);
                cordova.getActivity().startActivityForResult(intent, INSTALL_PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        else {
            try {
                if (Settings.Secure.getInt(cordova.getActivity().getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS) != 1) {
                    Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    cordova.setActivityResultCallback(this);
                    cordova.getActivity().startActivityForResult(intent, UNKNOWN_SOURCES_PERMISSION_REQUEST_CODE);
                    return false;
                }
            }
            catch (Settings.SettingNotFoundException e) {}
        }

        return true;
    }
    
    // React to user's response to our request for other permissions.
    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == OTHER_PERMISSIONS_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    this.updateManager.permissionDenied("Permission Denied: " + permissions[i]);
                    return;
                }
            }
            startInstall(); 
        }
    }
}
