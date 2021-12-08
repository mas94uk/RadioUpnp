/*
 * Copyright (c) 2018. Stephane Treuchot
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to
 * do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.watea.radio_upnp.activity;

import static com.watea.radio_upnp.activity.MainActivity.RADIO_ICON_SIZE;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.watea.radio_upnp.R;
import com.watea.radio_upnp.adapter.DlnaDevicesAdapter;
import com.watea.radio_upnp.adapter.RadiosAdapter;
import com.watea.radio_upnp.adapter.UpnpRegistryAdapter;
import com.watea.radio_upnp.model.DlnaDevice;
import com.watea.radio_upnp.model.Radio;
import com.watea.radio_upnp.service.NetworkProxy;

import org.fourthline.cling.model.meta.RemoteDevice;

import java.util.List;

public class MainFragment
  extends MainActivityFragment
  implements RadiosAdapter.Listener, UpnpRegistryAdapter.Listener {
  // <HMI assets
  private View dlnaView;
  private RecyclerView dlnaRecyclerView;
  private RecyclerView radiosRecyclerView;
  private FrameLayout defaultFrameLayout;
  private MenuItem dlnaMenuItem;
  private MenuItem preferredMenuItem;
  private AlertDialog dlnaAlertDialog;
  private AlertDialog radioLongPressAlertDialog;
  private AlertDialog dlnaEnableAlertDialog;
  private AlertDialog preferredRadiosAlertDialog;
  // />
  private boolean isPreferredRadios = false;
  private boolean gotItRadioLongPress;
  private boolean gotItDlnaEnable;
  private boolean gotItPreferredRadios;
  private RadiosAdapter radiosAdapter;
  private NetworkProxy networkProxy = null;
  // DLNA devices management
  private DlnaDevicesAdapter dlnaDevicesAdapter = null;

  private static int getRadiosColumnCount(@NonNull Configuration newConfig) {
    return (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) ? 5 : 3;
  }

  @Override
  public void onClick(@NonNull Radio radio) {
    if (networkProxy.isDeviceOffline()) {
      tell(R.string.no_internet);
    } else {
      assert getMainActivity() != null;
      getMainActivity().startReading(radio);
      if (!gotItRadioLongPress) {
        radioLongPressAlertDialog.show();
      }
    }
  }

  @Nullable
  @Override
  public Radio getRadioFromId(@NonNull Long radioId) {
    return getRadioLibrary().getFrom(radioId);
  }

  @Override
  public void onResume() {
    super.onResume();
    setRadiosView();
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu) {
    dlnaMenuItem = menu.findItem(R.id.action_dlna);
    preferredMenuItem = menu.findItem(R.id.action_preferred);
    setDlnaMenuItem();
    setPreferredMenuItem();
  }

  @NonNull
  @Override
  public View.OnClickListener getFloatingActionButtonOnClickListener() {
    return v -> {
      if (networkProxy.hasWifiIpAddress()) {
        assert getMainActivity() != null;
        if (getMainActivity().upnpSearch()) {
          dlnaAlertDialog.show();
          if (!gotItDlnaEnable) {
            dlnaEnableAlertDialog.show();
          }
        }
      } else {
        tell(R.string.LAN_required);
      }
    };
  }

  @NonNull
  @Override
  public View.OnLongClickListener getFloatingActionButtonOnLongClickListener() {
    return v -> {
      if (networkProxy.hasWifiIpAddress()) {
        assert getMainActivity() != null;
        if (getMainActivity().upnpReset()) {
          dlnaDevicesAdapter.clear();
          tell(R.string.dlna_search_reset);
        }
      } else {
        tell(R.string.LAN_required);
      }
      return true;
    };
  }

  @Override
  public int getFloatingActionButtonResource() {
    return R.drawable.ic_cast_blue_24dp;
  }

  @Override
  public int getMenuId() {
    return R.menu.menu_main;
  }

  @Override
  public int getTitle() {
    return R.string.title_main;
  }

  @Override
  protected void onActivityCreatedFiltered(@Nullable Bundle savedInstanceState) {
    // Context exists
    assert getContext() != null;
    assert getActivity() != null;
    assert getMainActivity() != null;
    // Restore saved state, if any
    String chosenDlnaDeviceIdentity = null;
    if (savedInstanceState != null) {
      isPreferredRadios = savedInstanceState.getBoolean(getString(R.string.key_preferred_radios));
      chosenDlnaDeviceIdentity =
        savedInstanceState.getString(getString(R.string.key_selected_device));
    }
    // Shared preferences
    SharedPreferences sharedPreferences = getActivity().getPreferences(Context.MODE_PRIVATE);
    gotItRadioLongPress =
      sharedPreferences.getBoolean(getString(R.string.key_radio_long_press_got_it), false);
    gotItDlnaEnable =
      sharedPreferences.getBoolean(getString(R.string.key_dlna_enable_got_it), false);
    gotItPreferredRadios =
      sharedPreferences.getBoolean(getString(R.string.key_preferred_radios_got_it), false);
    // Network
    networkProxy = getMainActivity().getNetworkProxy();
    // Adapters
    dlnaDevicesAdapter = new DlnaDevicesAdapter(
      chosenDlnaDeviceIdentity,
      new DlnaDevicesAdapter.Listener() {
        @Override
        public void onRowClick(@NonNull DlnaDevice dlnaDevice, boolean isChosen) {
          if (isChosen) {
            getMainActivity().startReading(null);
            tell(getResources().getString(R.string.dlna_selection) + dlnaDevice);
          } else {
            tell(R.string.no_dlna_selection);
          }
          dlnaAlertDialog.dismiss();
        }

        @Override
        public void onChosenDeviceChange() {
          // Do nothing if not yet created or if we were disposed
          if ((dlnaMenuItem != null) && isActuallyAdded()) {
            setDlnaMenuItem();
          }
        }
      },
      getContext());
    radiosAdapter = new RadiosAdapter(getContext(), this, RADIO_ICON_SIZE / 2);
    radiosRecyclerView.setLayoutManager(new GridLayoutManager(
      getContext(), getRadiosColumnCount(getActivity().getResources().getConfiguration())));
    radiosRecyclerView.setAdapter(radiosAdapter);
    // Build alert dialogs
    radioLongPressAlertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
      .setMessage(R.string.radio_long_press)
      .setPositiveButton(R.string.got_it, (dialogInterface, i) -> gotItRadioLongPress = true)
      .create();
    dlnaEnableAlertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
      .setMessage(R.string.dlna_enable)
      .setPositiveButton(R.string.got_it, (dialogInterface, i) -> gotItDlnaEnable = true)
      .create();
    preferredRadiosAlertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
      .setMessage(R.string.preferred_radios)
      .setPositiveButton(R.string.got_it, (dialogInterface, i) -> gotItPreferredRadios = true)
      .create();
    // Specific DLNA devices dialog
    dlnaAlertDialog = new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
      .setView(dlnaView)
      .create();
    dlnaRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    dlnaRecyclerView.setAdapter(dlnaDevicesAdapter);
  }

  @Nullable
  @Override
  protected View onCreateViewFiltered(
    @NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
    // Inflate the view so that graphical objects exists
    final View view = inflater.inflate(R.layout.content_main, container, false);
    radiosRecyclerView = view.findViewById(R.id.radios_recycler_view);
    defaultFrameLayout = view.findViewById(R.id.view_radios_default);
    dlnaView = inflater.inflate(R.layout.view_dlna_devices, container, false);
    dlnaRecyclerView = dlnaView.findViewById(R.id.dlna_devices_recycler_view);
    return view;
  }

  @Override
  public void onAddOrReplace(RemoteDevice remoteDevice) {
    if (dlnaDevicesAdapter != null) {
      dlnaDevicesAdapter.addOrReplace(remoteDevice);
    }
  }

  @Override
  public void onRemove(RemoteDevice remoteDevice) {
    if (dlnaDevicesAdapter != null) {
      dlnaDevicesAdapter.remove(remoteDevice);
    }
  }

  @Override
  public void onResetRemoteDevices() {
    if (dlnaDevicesAdapter != null) {
      dlnaDevicesAdapter.clear();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(getString(R.string.key_preferred_radios), isPreferredRadios);
    // May not exists
    DlnaDevice chosenDlnaDevice =
      (dlnaDevicesAdapter == null) ? null : dlnaDevicesAdapter.getChosenDlnaDevice();
    outState.putString(
      getString(R.string.key_selected_device),
      (chosenDlnaDevice == null) ? null : chosenDlnaDevice.getIdentity());
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    final GridLayoutManager gridLayoutManager =
      (GridLayoutManager) radiosRecyclerView.getLayoutManager();
    assert gridLayoutManager != null;
    gridLayoutManager.setSpanCount(getRadiosColumnCount(newConfig));
  }

  @Override
  public void onPause() {
    super.onPause();
    // Context exists
    assert getActivity() != null;
    // Shared preferences
    getActivity()
      .getPreferences(Context.MODE_PRIVATE)
      .edit()
      .putBoolean(getString(R.string.key_radio_long_press_got_it), gotItRadioLongPress)
      .putBoolean(getString(R.string.key_dlna_enable_got_it), gotItDlnaEnable)
      .putBoolean(getString(R.string.key_preferred_radios_got_it), gotItPreferredRadios)
      .apply();
  }

  @SuppressLint("NonConstantResourceId")
  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    switch (item.getItemId()) {
      case R.id.action_preferred:
        isPreferredRadios = !isPreferredRadios;
        setPreferredMenuItem();
        setRadiosView();
        if (!gotItPreferredRadios) {
          preferredRadiosAlertDialog.show();
        }
        return true;
      case R.id.action_dlna:
        dlnaDevicesAdapter.removeChosenDlnaDevice();
        dlnaMenuItem.setVisible(false);
        tell(R.string.no_dlna_selection);
        return true;
      default:
        // If we got here, the user's action was not recognized
        return false;
    }
  }

  @Nullable
  public DlnaDevice getChosenDlnaDevice() {
    return dlnaDevicesAdapter.getChosenDlnaDevice();
  }

  // Utility to set radio list views
  private void setRadiosView() {
    List<Long> radios = isPreferredRadios ?
      getRadioLibrary().getPreferredRadioIds() : getRadioLibrary().getAllRadioIds();
    radiosAdapter.onRefresh(radios);
    defaultFrameLayout.setVisibility(radios.isEmpty() ? View.VISIBLE : View.INVISIBLE);
  }

  private void setDlnaMenuItem() {
    Bitmap icon = dlnaDevicesAdapter.getChosenDlnaDeviceIcon();
    dlnaMenuItem.setVisible((icon != null));
    if (icon != null) {
      dlnaMenuItem.setIcon(new BitmapDrawable(getResources(), icon));
    }
  }

  private void setPreferredMenuItem() {
    preferredMenuItem.setIcon(
      isPreferredRadios ? R.drawable.ic_star_white_30dp : R.drawable.ic_star_border_white_30dp);
  }
}