package com.koala.data.models.kugou.AlbumMusicInfo.custom;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author koala
 * @version 1.0
 * @date 2023/6/20 21:54
 * @description
 */
@Data
public class KugouAlbumCustomMusicInfoModel implements Serializable {
    @SerializedName("album_audio_id")
    private String albumAudioId;
    @SerializedName("audio_id")
    private String audioId;
    @SerializedName("lyric_id")
    private String lyricId;
    @SerializedName("author_name")
    private String authorName;
    @SerializedName("ori_audio_name")
    private String oriAudioName;
    @SerializedName("official_songname")
    private String officialSongname;
    private String songname;
    private String remark;
    @SerializedName("suffix_audio_name")
    private String suffixAudioName;
    @SerializedName("publish_date")
    private Date publishDate;
    private String extname;
    @SerializedName("album_info")
    private AlbumInfoModel albumInfo;
    @SerializedName("audio_info")
    private AudioInfoModel audioInfo;
}
