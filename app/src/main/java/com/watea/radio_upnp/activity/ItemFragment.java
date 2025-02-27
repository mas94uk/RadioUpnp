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

import static android.app.Activity.RESULT_OK;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SimpleAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.watea.radio_upnp.R;
import com.watea.radio_upnp.adapter.PlayerAdapter;
import com.watea.radio_upnp.model.Radio;
import com.watea.radio_upnp.service.RadioURL;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public abstract class ItemFragment extends MainActivityFragment {
  private static final String LOG_TAG = ItemFragment.class.getName();
  private static final String DAR_FM_API = "http://api.dar.fm/";
  private static final String DAR_FM_PLAYLIST_REQUEST = DAR_FM_API + "playlist.php?q=@callsign%20";
  private static final String DAR_FM_PAGESIZE = "&pagesize=50";
  private static final String DAR_FM_STATIONS_REQUEST = DAR_FM_API + "darstations.php?station_id=";
  private static final String SPACE_FOR_SEARCH = "%20";
  private static final String COUNTRY_FOR_SEARCH = "@country%20";
  private static final String DAR_FM_PARTNER_TOKEN = "&partner_token=6453742475";
  private static final String DAR_FM_BASE_URL = "http://stream.dar.fm/";
  private static final String DAR_FM_NAME = "name";
  private static final String DAR_FM_WEB_PAGE = "web_page";
  private static final String DAR_FM_ID = "id";
  private static final int BROWSE_INTENT = 7;
  // <HMI assets
  protected EditText nameEditText;
  protected EditText urlEditText;
  protected EditText webPageEditText;
  protected EditText iconEditText;
  protected UrlWatcher urlWatcher;
  protected UrlWatcher webPageWatcher;
  protected UrlWatcher iconWatcher;
  private EditText countryEditText;
  private RadioButton darFmRadioButton;
  private ImageButton searchImageButton;
  private ProgressBar progressBar;
  private LinearLayout iconLinearLayout;
  // />

  @NonNull
  private static String extractValue(@NonNull Element element, @NonNull String tag) {
    final Elements elements = element.getElementsByTag(tag);
    return elements.isEmpty() ? "" : elements.first().ownText();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    final Context context = getContext();
    // Do nothing if we were disposed
    if ((context != null) && (requestCode == BROWSE_INTENT)) {
      Uri uri;
      Bitmap bitmap = null;
      if ((resultCode == RESULT_OK) && (data != null) && ((uri = data.getData()) != null)) {
        try {
          bitmap = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri));
          if (bitmap != null) {
            setRadioIcon(bitmap);
          }
        } catch (FileNotFoundException fileNotFoundException) {
          Log.i(LOG_TAG, "Error performing icon local search", fileNotFoundException);
        }
      }
      if (bitmap == null) {
        tell(R.string.no_local_icon);
      }
    }
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    // Default icon
    setRadioIcon(getMainActivity().getDefaultIcon());
    // Restore icon
    if (savedInstanceState != null) {
      try {
        final String file = savedInstanceState.getString(getString(R.string.key_radio_icon_file));
        setRadioIcon(BitmapFactory.decodeFile(file));
      } catch (Exception exception) {
        Log.e(LOG_TAG, "onActivityCreated: internal failure restoring context", exception);
      }
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    // Save icon; may fail
    try {
      final Bitmap icon = getIcon();
      if (icon != null) {
        final File file = Radio.storeToFile(getMainActivity(), icon, Integer.toString(hashCode()));
        outState.putString(getString(R.string.key_radio_icon_file), file.getPath());
      }
    } catch (FileNotFoundException fileNotFoundException) {
      Log.e(LOG_TAG, "onSaveInstanceState: internal failure", fileNotFoundException);
    }
  }

  @Override
  public void onPause() {
    flushKeyboard();
    super.onPause();
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    flushKeyboard();
    if (item.getItemId() == R.id.action_done)
      if (urlWatcher.url == null) {
        tell(R.string.radio_definition_error);
      } else {
        // Action shall be implemented by actual class
        return false;
      }
    else {
      // If we got here, the user's action was not recognized
      // Invoke the superclass to handle it
      return super.onOptionsItemSelected(item);
    }
    return true;
  }

  @NonNull
  @Override
  public View.OnClickListener getFloatingActionButtonOnClickListener() {
    return v -> {
      if (getNetworkProxy().isDeviceOffline()) {
        tell(R.string.no_internet);
      } else if (urlWatcher.url == null) {
        tell(R.string.connection_test_aborted);
      } else {
        new UrlTester(urlWatcher.url);
      }
    };
  }

  @Override
  public int getFloatingActionButtonResource() {
    return R.drawable.ic_radio_white_24dp;
  }

  @Override
  public int getMenuId() {
    return R.menu.menu_item_modify;
  }

  @Override
  protected int getLayout() {
    return R.layout.content_item;
  }

  @Override
  public void onCreateView(@NonNull View view) {
    nameEditText = view.findViewById(R.id.name_edit_text);
    countryEditText = view.findViewById(R.id.country_edit_text);
    progressBar = view.findViewById(R.id.progress_bar);
    urlEditText = view.findViewById(R.id.url_edit_text);
    iconEditText = view.findViewById(R.id.icon_edit_text);
    iconLinearLayout = view.findViewById(R.id.icon_linear_layout);
    webPageEditText = view.findViewById(R.id.web_page_edit_text);
    darFmRadioButton = view.findViewById(R.id.dar_fm_radio_button);
    searchImageButton = view.findViewById(R.id.search_image_button);
    final View countryEditLinearlayout = view.findViewById(R.id.country_edit_linearlayout);
    // Order matters!
    ((RadioGroup) view.findViewById(R.id.search_radio_group)).setOnCheckedChangeListener(
      (group, checkedId) -> {
        boolean isDarFmSelected = (checkedId == R.id.dar_fm_radio_button);
        searchImageButton.setImageResource(isDarFmSelected ?
          R.drawable.ic_search_white_40dp : R.drawable.ic_baseline_image_search_white_40dp);
        webPageEditText.setEnabled(!isDarFmSelected);
        urlEditText.setEnabled(!isDarFmSelected);
        if (isDarFmSelected) {
          iconEditText.setText("");
        }
        iconLinearLayout.setVisibility(isDarFmSelected ? View.GONE : View.VISIBLE);
        countryEditLinearlayout.setVisibility(getVisibleFrom(isDarFmSelected));
      });
    searchImageButton.setOnClickListener(v -> {
      flushKeyboard();
      if (getNetworkProxy().isDeviceOffline()) {
        tell(R.string.no_internet);
      } else if (darFmRadioButton.isChecked()) {
        new DarFmSearcher();
      } else {
        final URL iconUrl = iconWatcher.url;
        final URL webPageUrl = webPageWatcher.url;
        if ((iconUrl == null) && (webPageUrl == null)) {
          tell(R.string.no_icon_found);
        } else {
          // Search in icon URL if available
          if (iconUrl == null) {
            new IconWebSearcher(webPageUrl);
          } else {
            new IconUrlSearcher(iconUrl);
          }
        }
      }
    });
    view.findViewById(R.id.browse_button).setOnClickListener(v ->
      startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("*/*"), BROWSE_INTENT));
    urlWatcher = new UrlWatcher(urlEditText);
    webPageWatcher = new UrlWatcher(webPageEditText);
    iconWatcher = new UrlWatcher(iconEditText);
  }

  @NonNull
  protected String getRadioName() {
    return nameEditText.getText().toString().toUpperCase();
  }

  @Nullable
  protected Bitmap getIcon() {
    // nameEditText may be null if called before creation
    return (nameEditText == null) ? null : (Bitmap) nameEditText.getTag();
  }

  protected void setRadioIcon(@NonNull Bitmap icon) {
    // Resize bitmap as a square
    final int height = icon.getHeight();
    final int width = icon.getWidth();
    final int min = Math.min(height, width);
    icon = Bitmap.createBitmap(icon, (width - min) / 2, (height - min) / 2, min, min, null, false);
    final Drawable drawable =
      new BitmapDrawable(getResources(), MainActivity.createScaledBitmap(icon));
    nameEditText.setCompoundDrawablesRelativeWithIntrinsicBounds(null, drawable, null, null);
    // radioIcon stored as tag
    nameEditText.setTag(icon);
  }

  private void flushKeyboard() {
    assert getView() != null;
    assert getContext() != null;
    ((InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE))
      .hideSoftInputFromWindow(getView().getWindowToken(), 0);
  }

  private void showSearchButton(boolean isShowing) {
    searchImageButton.setVisibility(getVisibleFrom(isShowing));
    progressBar.setVisibility(getVisibleFrom(!isShowing));
  }

  private void tellWait() {
    flushKeyboard();
    tell(R.string.wait_search);
  }

  private void tellSearch() {
    showSearchButton(false);
    tellWait();
  }

  // Utility class to listen for URL edition
  protected class UrlWatcher implements TextWatcher {
    private final int defaultColor;
    @NonNull
    private final EditText editText;
    @Nullable
    protected URL url = null;

    private UrlWatcher(@NonNull EditText editText) {
      this.editText = editText;
      this.editText.addTextChangedListener(this);
      defaultColor = this.editText.getCurrentTextColor();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
      try {
        url = (s.length() > 0) ? new URL(s.toString()) : null;
      } catch (MalformedURLException malformedURLException) {
        url = null;
      }
      editText.setTextColor(
        (url == null) ? ContextCompat.getColor(getMainActivity(), R.color.dark_red) : defaultColor);
    }
  }

  // Abstract class to handle web search
  private abstract class Searcher extends Thread {

    @Override
    public void run() {
      onSearch();
      new Handler(Looper.getMainLooper()).post(() -> {
        if (isActuallyAdded()) {
          onPostSearch();
        }
      });
    }

    protected abstract void onSearch();

    protected abstract void onPostSearch();
  }

  private abstract class IconSearcher extends Searcher {
    @NonNull
    protected final URL url;
    @Nullable
    protected Bitmap foundIcon = null;

    private IconSearcher(@NonNull URL url) {
      super();
      this.url = url;
      tellSearch();
      start();
    }

    @Override
    protected void onPostSearch() {
      showSearchButton(true);
      if (foundIcon == null) {
        tell(R.string.no_icon_found);
      } else {
        setRadioIcon(foundIcon);
        tell(R.string.icon_updated);
      }
    }
  }

  private class IconWebSearcher extends IconSearcher {
    private IconWebSearcher(@NonNull URL url) {
      super(url);
    }

    @Override
    protected void onSearch() {
      foundIcon = RadioURL.iconSearch(url);
    }
  }

  private class IconUrlSearcher extends IconSearcher {
    private IconUrlSearcher(@NonNull URL url) {
      super(url);
    }

    @Override
    protected void onSearch() {
      foundIcon = new RadioURL(url).getBitmap();
    }
  }

  // Launch with no parameter: display the list result or fill content if only one result
  // Launch with one parameter: fill content
  private class DarFmSearcher extends Searcher {
    // List of radio datas
    private final List<Map<String, String>> radios = new Vector<>();
    @Nullable
    private Bitmap foundIcon = null;

    private DarFmSearcher() {
      this(null);
    }

    private DarFmSearcher(@Nullable Map<String, String> radioDatas) {
      super();
      if (radioDatas != null) {
        radios.add(radioDatas);
      }
      tellSearch();
      start();
    }

    @Override
    protected void onSearch() {
      if (radios.isEmpty()) {
        try {
          final Element search = Jsoup
            .connect(DAR_FM_PLAYLIST_REQUEST +
              getRadioName().replace(" ", SPACE_FOR_SEARCH) + getCountrySearch() +
              DAR_FM_PAGESIZE + DAR_FM_PARTNER_TOKEN)
            .get();
          // Parse data
          for (Element station : search.getElementsByTag("station")) {
            // As stated, may fail
            try {
              final Map<String, String> radioMap = new Hashtable<>();
              radioMap.put(DAR_FM_ID, extractValue(station, "station_id"));
              radioMap.put(DAR_FM_NAME, extractValue(station, "callsign"));
              addToRadiosInOrder(radioMap);
            } catch (Exception exception) {
              Log.i(LOG_TAG, "Error performing DAR_FM_PLAYLIST_REQUEST extraction", exception);
            }
          }
        } catch (IOException iOexception) {
          Log.i(LOG_TAG, "Error performing DAR_FM_PLAYLIST_REQUEST search", iOexception);
        }
      }
      // When radio is known, fetch data
      if (radios.size() == 1) {
        final Map<String, String> foundRadio = radios.get(0);
        try {
          final Element station = Jsoup
            .connect(DAR_FM_STATIONS_REQUEST + foundRadio.get(DAR_FM_ID) + DAR_FM_PARTNER_TOKEN)
            .get();
          foundRadio.put(DAR_FM_WEB_PAGE, extractValue(station, "websiteurl"));
          // Order matters
          foundIcon = new RadioURL(new URL(extractValue(station, "imageurl"))).getBitmap();
        } catch (MalformedURLException malformedURLException) {
          Log.i(LOG_TAG, "Error performing icon search", malformedURLException);
        } catch (IOException iOexception) {
          Log.i(LOG_TAG, "Error performing DAR_FM_STATIONS_REQUEST search", iOexception);
          radios.clear();
        }
      }
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onPostSearch() {
      switch (radios.size()) {
        case 0:
          tell(R.string.dar_fm_failed);
          break;
        case 1:
          final Map<String, String> foundRadio = radios.get(0);
          final String radioName = foundRadio.get(DAR_FM_NAME);
          if (radioName != null) {
            nameEditText.setText(radioName.toUpperCase());
          }
          urlEditText.setText(DAR_FM_BASE_URL + foundRadio.get(DAR_FM_ID));
          final boolean isDarFmWebPageFound = foundRadio.containsKey(DAR_FM_WEB_PAGE);
          final boolean isIconFound = (foundIcon != null);
          if (isDarFmWebPageFound) {
            webPageEditText.setText(foundRadio.get(DAR_FM_WEB_PAGE));
          }
          if (isIconFound) {
            setRadioIcon(foundIcon);
          }
          tell((isIconFound && isDarFmWebPageFound) ?
            R.string.dar_fm_done : R.string.dar_fm_went_wrong);
          break;
        default:
          assert getContext() != null;
          new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
            .setAdapter(
              new SimpleAdapter(
                getContext(),
                radios,
                R.layout.row_darfm_radio,
                new String[]{DAR_FM_NAME},
                new int[]{R.id.row_darfm_radio_name_text_view}),
              // Call recursively for selected radio
              (dialogInterface, i) -> new DarFmSearcher(radios.get(i)))
            .create()
            .show();
      }
      showSearchButton(true);
    }

    @NonNull
    private String getCountrySearch() {
      final String countrySearch =
        countryEditText.getText().toString().toUpperCase().replace(" ", "");
      return (countrySearch.length() > 0) ? COUNTRY_FOR_SEARCH + countrySearch : "";
    }

    private void addToRadiosInOrder(Map<String, String> radioMap) {
      String newName = radioMap.get(DAR_FM_NAME);
      if (newName != null) {
        newName = newName.toLowerCase();
        for (int index = 0; index < radios.size(); index++) {
          final String name = radios.get(index).get(DAR_FM_NAME);
          if ((name == null) || (name.toLowerCase().compareTo(newName) > 0)) {
            radios.add(index, radioMap);
            return;
          }
        }
      }
      radios.add(radioMap);
    }
  }

  private class UrlTester extends Searcher {
    @NonNull
    private final URL url;
    @Nullable
    private String streamContent = null;

    private UrlTester(@NonNull URL url) {
      super();
      this.url = url;
      tellWait();
      start();
    }

    @Override
    protected void onSearch() {
      final URL uRL = Radio.getUrlFromM3u(url);
      streamContent = (uRL == null) ? null : new RadioURL(uRL).getStreamContentType();
    }

    @Override
    protected void onPostSearch() {
      if (streamContent == null) {
        tell(R.string.connection_test_failed);
      } else if (!PlayerAdapter.isHandling(streamContent)) {
        tell(getResources().getString(R.string.mime_not_authorized) + streamContent + ".");
      } else {
        tell(getResources().getString(R.string.connection_test_successful) + streamContent + ".");
      }
    }
  }
}