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

package com.watea.radio_upnp.adapter;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.watea.radio_upnp.activity.MainActivity;
import com.watea.radio_upnp.model.Radio;
import com.watea.radio_upnp.model.RadioLibrary;

import java.util.List;
import java.util.Vector;

public abstract class RadiosAdapter<V extends RadiosAdapter<?>.ViewHolder>
  extends RecyclerView.Adapter<V> {
  private static final int DEFAULT = -1;
  @NonNull
  protected final Listener listener;
  protected final List<Long> radioIds = new Vector<>();
  private final int resource;
  @Nullable
  protected RadioLibrary radioLibrary = null;
  private int currentRadioIndex = DEFAULT;
  private boolean isPreferred = false;
  @NonNull
  private final RadioLibrary.Listener radioLibraryListener = new RadioLibrary.Listener() {
    @Override
    public void onPreferredChange(@NonNull Radio radio) {
      notifyItemChanged(getIndexOf(radio));
    }

    @Override
    public void onNewCurrentRadio(@Nullable Radio radio) {
      final int previousCurrentRadioIndex = currentRadioIndex;
      currentRadioIndex = getIndexOf(radio);
      notifyItemChanged(previousCurrentRadioIndex);
      notifyItemChanged(currentRadioIndex);
    }

    @Override
    public void onAdd(@NonNull Long radioId) {
      // Don't add to view if preferred switch is activated, as new radio if not preferred
      if (!isPreferred) {
        radioIds.add(radioId);
        notifyItemRangeInserted(getIndexOf(radioId), 1);
        onCountChange(false);
      }
    }

    @Override
    public void onRemove(@NonNull Long radioId) {
      final int index = getIndexOf(radioId);
      radioIds.remove(index);
      notifyItemRemoved(index);
      onCountChange(radioIds.isEmpty());
    }

    @Override
    public void onMove(@NonNull Long fromId, @NonNull Long toId) {
      final int fromIndex = getIndexOf(fromId);
      final int toIndex = getIndexOf(toId);
      radioIds.set(toIndex, fromId);
      radioIds.set(fromIndex, toId);
      notifyItemMoved(fromIndex, toIndex);
    }
  };

  public RadiosAdapter(
    @NonNull Listener listener, int resource, @NonNull RecyclerView recyclerView) {
    this.listener = listener;
    this.resource = resource;
    // Adapter shall be defined for RecyclerView
    recyclerView.setAdapter(this);
  }

  @NonNull
  public static Bitmap createScaledBitmap(@NonNull Bitmap bitmap, int size) {
    return Bitmap.createScaledBitmap(bitmap, size, size, true);
  }

  public void unset() {
    if (radioLibrary != null) {
      radioLibrary.removeListener(radioLibraryListener);
    }
  }

  @Override
  public void onBindViewHolder(@NonNull V v, int i) {
    assert radioLibrary != null;
    if (radioLibrary.isOpen()) {
      final Radio radio = radioLibrary.getFrom(radioIds.get(i));
      assert radio != null;
      v.setView(radio);
    }
  }

  @Override
  public int getItemCount() {
    return radioIds.size();
  }

  @SuppressLint("NotifyDataSetChanged")
  public void refresh(boolean isPreferred) {
    this.isPreferred = isPreferred;
    radioIds.clear();
    assert radioLibrary != null;
    radioIds.addAll(
      isPreferred ? radioLibrary.getPreferredRadioIds() : radioLibrary.getAllRadioIds());
    currentRadioIndex = getIndexOf(radioLibrary.getCurrentRadio());
    notifyDataSetChanged();
    onCountChange(radioIds.isEmpty());
  }

  // Must be called
  public void set(@NonNull RadioLibrary radioLibrary, boolean isPreferred) {
    this.radioLibrary = radioLibrary;
    this.radioLibrary.addListener(radioLibraryListener);
    refresh(isPreferred);
  }

  @NonNull
  protected View getView(@NonNull ViewGroup viewGroup) {
    return LayoutInflater.from(viewGroup.getContext()).inflate(resource, viewGroup, false);
  }

  protected int getIndexOf(@NonNull Long radioId) {
    return radioIds.indexOf(radioId);
  }

  protected int getIndexOf(@Nullable Radio radio) {
    return (radio == null) ? DEFAULT : getIndexOf(radio.getId());
  }

  protected void onCountChange(boolean isEmpty) {
    listener.onCountChange(isEmpty);
  }

  public interface Listener {
    void onClick(@NonNull Radio radio);

    void onCountChange(boolean isEmpty);
  }

  protected class ViewHolder extends RecyclerView.ViewHolder {
    @NonNull
    protected final TextView radioTextView;
    @NonNull
    protected Radio radio = Radio.DUMMY_RADIO;

    protected ViewHolder(@NonNull View itemView) {
      super(itemView);
      radioTextView = getRadioTextView(itemView);
      radioTextView.setOnClickListener(v -> listener.onClick(radio));
    }

    @NonNull
    protected TextView getRadioTextView(@NonNull View itemView) {
      return (TextView) itemView;
    }

    protected void setImage(@NonNull BitmapDrawable bitmapDrawable) {
      radioTextView
        .setCompoundDrawablesRelativeWithIntrinsicBounds(null, bitmapDrawable, null, null);
    }

    protected void setView(@NonNull Radio radio) {
      this.radio = radio;
      setImage(new BitmapDrawable(
        radioTextView.getResources(),
        createScaledBitmap(this.radio.getIcon(), MainActivity.getSmallIconSize())));
      radioTextView.setText(this.radio.getName());
    }

    protected boolean isCurrentRadio() {
      assert radio != Radio.DUMMY_RADIO;
      return (getIndexOf(radio) == currentRadioIndex);
    }
  }
}