import 'dart:async';

import 'package:flutter/services.dart';

class ChromecastApi {
  static const MethodChannel _channel =
    const MethodChannel('chromecast_api');

  static Future<void> activateSubtitles(int id) async {
    return await _channel.invokeMethod('activateSubtitles', id);
  }

  static Future<void> loadMedia(MediaInfo mediaInfo) async {
    return await _channel.invokeMethod('loadMedia', mediaInfo.toMap());
  }

  static Future<void> showCastDialog() async =>
    await _channel.invokeMethod('showCastDialog');

  static const EventChannel _castEventChannel =
    const EventChannel('cast_state_event');

  static Stream<dynamic> castEventStream() => _castEventChannel.receiveBroadcastStream();

  static const EventChannel _mediaEventChannel =
    const EventChannel('media_state_event');

  static Stream<dynamic> mediaEventStream() => _mediaEventChannel.receiveBroadcastStream();
}

class MediaInfo {
  int episode;
  List<Uri> images = [];
  int season;
  String seriesTitle;
  List<TextTrack> subtitles = [];
  String title;
  Uri url;

  final MediaMetadataType type;

  MediaInfo(this.type);

  Map<String, dynamic> toMap() => {
      'episode': this.episode != null && this.episode > 0 ? this.episode : null,
      'images': this.images.map((uri) => uri.toString()).toList(),
      'season': this.season != null && this.season > 0 ? this.season : null,
      'seriesTitle': this.seriesTitle,
      'subtitles': this.subtitles.map((subs) => subs.toMap()).toList(),
      'title': this.title,
      'url': this.url.toString(),
      'type': this.type.index
    };
}

class TextTrack {
  int id;
  String lang;
  String name;
  Uri url;

  Map<String, dynamic> toMap() => {
      'id': this.id,
      'lang': this.lang,
      'name': this.name,
      'url': this.url.toString()
    };
}

enum MediaMetadataType {
  GENERIC,
  MOVIE,
  TV_SHOW,
  MUSIC_TRACK,
  PHOTO,
  AUDIOBOOK_CHAPTER
}
