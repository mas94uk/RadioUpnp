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

import android.content.ContentValues;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.watea.radio_upnp.R;
import com.watea.radio_upnp.adapter.RadiosModifyAdapter;
import com.watea.radio_upnp.model.Radio;
import com.watea.radio_upnp.model.RadioSQLContract;

import java.util.List;

public class ModifyFragment
  extends
  MainActivityFragment<ModifyFragment.Callback>
  implements
  RadiosModifyAdapter.Listener {
  private static final String LOG_TAG = ModifyFragment.class.getSimpleName();
  // <HMI assets
  private View mRadiosDefaultView;
  // />
  private RadiosModifyAdapter mRadiosModifyAdapter;

  @Nullable
  @Override
  public View onCreateView(
    LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
    super.onCreateView(inflater, container, savedInstanceState);
    View view = inflater.inflate(R.layout.content_modify, container, false);
    RecyclerView radiosView = view.findViewById(R.id.radios_view);
    radiosView.setLayoutManager(new LinearLayoutManager(getActivity()));
    // RecyclerView shall be defined for Adapter
    mRadiosModifyAdapter = new RadiosModifyAdapter(getActivity(), this, radiosView);
    // Adapter shall be defined for RecyclerView
    radiosView.setAdapter(mRadiosModifyAdapter);
    mRadiosDefaultView = view.findViewById(R.id.radios_default_view);
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    // Update view even if no change
    List<Long> radioIds = mRadioLibrary.getAllRadioIds();
    mRadiosModifyAdapter.setRadioIds(radioIds);
    mRadiosDefaultView.setVisibility((radioIds.size() == 0) ? View.VISIBLE : View.INVISIBLE);
    // Decorate
    mCallback.onResume();
  }

  @Override
  @Nullable
  public Radio getRadioFromId(@NonNull Long radioId) {
    return mRadioLibrary.getFrom(radioId);
  }

  @Override
  public void onModifyClick(@NonNull Long radioId) {
    mCallback.onModifyRequest(radioId);
  }

  @Override
  public boolean onDelete(@NonNull Long radioId) {
    if (mRadioLibrary.deleteFrom(radioId) > 0) {
      // Default view is visible if no radio left
      mRadiosDefaultView.setVisibility(
        mRadioLibrary.getAllRadioIds().isEmpty() ? View.VISIBLE : View.INVISIBLE);
      return true;
    } else {
      Log.w(LOG_TAG, "Internal failure, radio database update failed");
      return false;
    }
  }

  @Override
  public boolean onMove(@NonNull Long fromRadioId, @NonNull Long toRadioId) {
    ContentValues fromPosition = positionContentValuesOf(fromRadioId);
    ContentValues toPosition = positionContentValuesOf(toRadioId);
    if ((mRadioLibrary.updateFrom(fromRadioId, toPosition) > 0) &&
      (mRadioLibrary.updateFrom(toRadioId, fromPosition) > 0)) {
      return true;
    } else {
      Log.w(LOG_TAG, "Internal failure, radio database update failed");
      return false;
    }
  }

  // Utility for database update of radio position
  @NonNull
  private ContentValues positionContentValuesOf(@NonNull Long radioId) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(
      RadioSQLContract.Columns.COLUMN_POSITION, mRadioLibrary.getPositionFrom(radioId));
    return contentValues;
  }

  public interface Callback {
    void onResume();

    void onModifyRequest(@NonNull Long radioId);
  }
}