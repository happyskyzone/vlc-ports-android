/**
 * **************************************************************************
 * BaseBrowserAdapter.java
 * ****************************************************************************
 * Copyright © 2015 VLC authors and VideoLAN
 * Author: Geoffrey Métais
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * ***************************************************************************
 */
package org.videolan.vlc.gui.browser;

import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;

import org.videolan.libvlc.Media;
import org.videolan.vlc.MediaDatabase;
import org.videolan.vlc.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.BrowserItemSeparatorBinding;
import org.videolan.vlc.databinding.DirectoryViewItemBinding;
import org.videolan.vlc.gui.audio.MediaComparators;
import org.videolan.vlc.util.CustomDirectories;
import org.videolan.vlc.util.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class BaseBrowserAdapter extends  RecyclerView.Adapter<RecyclerView.ViewHolder> implements View.OnLongClickListener {
    protected static final String TAG = "VLC/BaseBrowserAdapter";

    protected static final int TYPE_MEDIA = 0;
    protected static final int TYPE_SEPARATOR = 1;
    protected static final int TYPE_STORAGE = 2;

    protected int FOLDER_RES_ID = R.drawable.ic_menu_folder;

    ArrayList<Object> mMediaList = new ArrayList<Object>();
    BaseBrowserFragment fragment;
    MediaDatabase mDbManager;
    LinkedList<String> mMediaDirsLocation;
    List<String> mCustomDirsLocation;
    String mEmptyDirectoryString;

    public BaseBrowserAdapter(BaseBrowserFragment fragment){
        this.fragment = fragment;
        mEmptyDirectoryString = VLCApplication.getAppResources().getString(R.string.directory_empty);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder vh;
        View v;
        if (viewType == TYPE_MEDIA) {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.directory_view_item, parent, false);
            vh = new MediaViewHolder(v);
        } else {
            v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.browser_item_separator, parent, false);
            vh = new SeparatorViewHolder(v);
        }
        return vh;
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        if (viewType == TYPE_MEDIA) {
            onBindMediaViewHolder(holder, position);
        } else {
            SeparatorViewHolder vh = (SeparatorViewHolder) holder;
            vh.binding.setTitle(getItem(position).toString());
        }
    }

    private void onBindMediaViewHolder(final RecyclerView.ViewHolder holder, int position) {
        final MediaViewHolder vh = (MediaViewHolder) holder;
        final MediaWrapper media = (MediaWrapper) getItem(position);
        boolean hasContextMenu = (media.getType() == MediaWrapper.TYPE_AUDIO ||
                media.getType() == MediaWrapper.TYPE_VIDEO ||
                media.getType() == MediaWrapper.TYPE_DIR );
        vh.binding.setHandler(mClickHandler);
        vh.binding.setMedia(media);
        vh.binding.setHasContextMenu(hasContextMenu);
        vh.binding.setType(TYPE_MEDIA);
        vh.binding.executePendingBindings();

        vh.icon.setImageResource(getIconResId(media));
        if (hasContextMenu) {
            vh.itemView.setOnLongClickListener(this);
        }
    }

    @Override
    public int getItemCount() {
        return mMediaList.size();
    }

    public class MediaViewHolder extends RecyclerView.ViewHolder {
        public CheckBox checkBox;
        public ImageView icon;
        public ImageView more;
        DirectoryViewItemBinding binding;

        public MediaViewHolder(View v) {
            super(v);
            binding = DataBindingUtil.bind(v);
            checkBox = (CheckBox) v.findViewById(R.id.browser_checkbox);
            icon = (ImageView) v.findViewById(R.id.dvi_icon);
            more = (ImageView) v.findViewById(R.id.item_more);
            v.findViewById(R.id.layout_item).setTag(R.id.layout_item, this);
        }
    }

    public static class SeparatorViewHolder extends RecyclerView.ViewHolder {
        BrowserItemSeparatorBinding binding;

        public SeparatorViewHolder(View v) {
            super(v);
            binding = DataBindingUtil.bind(v);
        }
    }

    public static class Storage {
        Uri uri;
        String name;
        String description;

        public Storage(Uri uri){
            this.uri = uri;
            name = uri.getLastPathSegment();
        }

        public String getName() {
            return Uri.decode(name);
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public Uri getUri() {
            return uri;
        }
    }

    public void clear(){
        mMediaList.clear();
        notifyDataSetChanged();
    }

    public boolean isEmpty(){
        return mMediaList.isEmpty();
    }

    public void addItem(Media media, boolean notify, boolean top){
        MediaWrapper mediaWrapper = new MediaWrapper(media);
        addItem(mediaWrapper, notify, top);

    }

    public void addItem(Object item, boolean notify, boolean top){
        int position = top ? 0 : mMediaList.size();
        if (item instanceof MediaWrapper && ((MediaWrapper)item).getTitle().startsWith("."))
            return;
        else if (item instanceof Media)
            item = new MediaWrapper((Media) item);

        mMediaList.add(position, item);
        if (notify)
            notifyItemInserted(position);
    }

    public void setDescription(int position, String description){
        Object item = getItem(position);
        if (item instanceof MediaWrapper)
            ((MediaWrapper) item).setDescription(description);
        else if (item instanceof Storage)
            ((Storage) item).setDescription(description);
        else
            return;
        notifyItemChanged(position);
    }

    public void updateMediaDirs(){
        if (mDbManager == null)
            mDbManager = MediaDatabase.getInstance();
        if (mMediaDirsLocation == null)
            mMediaDirsLocation = new LinkedList<String>();
        else
            mMediaDirsLocation.clear();
        List<File> mediaDirs = mDbManager.getMediaDirs();
        for (File dir : mediaDirs){
            mMediaDirsLocation.add(dir.getPath());
        }
        mCustomDirsLocation = Arrays.asList(CustomDirectories.getCustomDirectories());
    }

    public void addAll(ArrayList<MediaWrapper> mediaList){
        mMediaList.clear();
        for (MediaWrapper mw : mediaList)
            mMediaList.add(mw);
    }

    public void removeItem(int position, boolean notify){
        mMediaList.remove(position);
        if (notify) {
            notifyItemRemoved(position);
        }
    }

    public Object getItem(int position){
        return mMediaList.get(position);
    }

    public int getItemViewType(int position){
        if (getItem(position) instanceof  MediaWrapper)
            return TYPE_MEDIA;
        else if (getItem(position) instanceof Storage)
            return TYPE_STORAGE;
        else
            return TYPE_SEPARATOR;
    }

    public void sortList(){
        ArrayList<MediaWrapper> files = new ArrayList<MediaWrapper>(), dirs = new ArrayList<MediaWrapper>();
        for (Object item : mMediaList){
            if (item instanceof MediaWrapper) {
                MediaWrapper media = (MediaWrapper) item;
                if (media.getType() == MediaWrapper.TYPE_DIR)
                    dirs.add(media);
                else
                    files.add(media);
            }
        }
        if (dirs.isEmpty() && files.isEmpty())
            return;
        mMediaList.clear();
        if (!dirs.isEmpty()) {
            Collections.sort(dirs, MediaComparators.byName);
            mMediaList.addAll(dirs);
        }
        if (!files.isEmpty()) {
            Collections.sort(files, MediaComparators.byName);
            mMediaList.addAll(files);
        }
        notifyDataSetChanged();
    }

    protected int getIconResId(MediaWrapper media) {
        switch (media.getType()){
            case MediaWrapper.TYPE_AUDIO:
                return R.drawable.ic_browser_audio_normal;
            case MediaWrapper.TYPE_DIR:
                return FOLDER_RES_ID;
            case MediaWrapper.TYPE_VIDEO:
                return R.drawable.ic_browser_video_normal;
            case MediaWrapper.TYPE_SUBTITLE:
                return R.drawable.ic_browser_subtitle_normal;
            default:
                return R.drawable.ic_browser_unknown_normal;
        }
    }

    public ClickHandler mClickHandler = new ClickHandler();
    public class ClickHandler {
        public void onClick(View v){
            openMediaFromView(v);
        }

        public void onCheckBoxClick(View v){
            checkBoxAction(v);
        }

        public void onMoreClick(View v){
            final MediaViewHolder holder = (MediaViewHolder) ((ViewGroup)v.getParent()).getTag(R.id.layout_item);
            fragment.onPopupMenu(holder.more, holder.getAdapterPosition());
        }
    }

    protected void openMediaFromView(View v) {
        final MediaViewHolder holder = (MediaViewHolder) v.getTag(R.id.layout_item);
        final MediaWrapper mw = (MediaWrapper) getItem(holder.getAdapterPosition());
        mw.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO);

        if (mw.getType() == MediaWrapper.TYPE_DIR)
            fragment.browse(mw, holder.getAdapterPosition(), true);
        else if (mw.getType() == MediaWrapper.TYPE_VIDEO)
            Util.openMedia(v.getContext(), mw);
        else  if (mw.getType() == MediaWrapper.TYPE_AUDIO) {
            int position = 0;
            LinkedList<MediaWrapper> mediaLocations = new LinkedList<MediaWrapper>();
            MediaWrapper mediaItem;
            for (Object item : mMediaList)
                if (item instanceof MediaWrapper) {
                    mediaItem = (MediaWrapper) item;
                    if (mediaItem.getType() == MediaWrapper.TYPE_VIDEO || mediaItem.getType() == MediaWrapper.TYPE_AUDIO) {
                        mediaLocations.add(mediaItem);
                        if (mediaItem.equals(mw))
                            position = mediaLocations.size() - 1;
                    }
                }
            Util.openList(v.getContext(), mediaLocations, position);
        } else {
            Util.openStream(v.getContext(), mw.getLocation());
        }
    }

    protected void checkBoxAction(View v){}

    @Override
    public boolean onLongClick(View v) {
        final MediaViewHolder holder = (MediaViewHolder) v.getTag(R.id.layout_item);
        fragment.mRecyclerView.openContextMenu(holder.getAdapterPosition());
        return true;
    }
}
