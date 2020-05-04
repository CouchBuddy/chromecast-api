import 'dart:async';

import 'package:flutter/services.dart';

class ChromecastApi {
  static const MethodChannel _channel =
    const MethodChannel('chromecast_api');

  // Methods

  static Future<void> activateSubtitles(int id) async {
    if (id == null || id <= 0) {
      print('ID must be a positive number');
      return;
    }
    return await _channel.invokeMethod('activateSubtitles', id);
  }

  static Future<void> loadMedia(MediaInfo mediaInfo) async =>
    await _channel.invokeMethod('loadMedia', mediaInfo.toMap());

  static Future<void> playOrPause() async =>
    await _channel.invokeMethod('playOrPause');

  static Future<void> showCastDialog() async =>
    await _channel.invokeMethod('showCastDialog');

  // Event Listeners

  static const EventChannel _castEventChannel = const EventChannel('cast_state_event');
  static Stream<dynamic> castEventStream = _castEventChannel.receiveBroadcastStream();

  static const EventChannel _mediaEventChannel = const EventChannel('media_state_event');
  static Stream<dynamic> mediaEventStream = _mediaEventChannel.receiveBroadcastStream();
}

class MediaInfo {
  int episode;
  List<Uri> images = [];
  int season;
  String seriesTitle;
  List<TextTrack> subtitles = [];
  String title;
  Uri url;

  MediaMetadataType type;

  MediaInfo(this.type);

  MediaInfo.fromMap(Map<String, dynamic> data) {
    this.episode = data['episode'] as int;
    this.images = data['images'] != null
      ? data['images'].map<Uri>((url) => Uri.parse(url)).toList()
      : [];
    this.season = data['season'] as int;
    this.seriesTitle = data['seriesTitle'];
    this.subtitles = data['subtitles'] != null
      ? data['subtitles'].map<TextTrack>((subs) => TextTrack.fromMap(Map<String, dynamic>.from(subs))).toList()
      : [];
    this.title = data['title'];
    this.url = Uri.parse(data['url']);
    this.type = MediaMetadataType.values[data['type'] as int];
  }

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
  /// tells if the track is active
  ///
  /// This is a read-only property, setting its value to true
  /// will not enable the subtitles on the cast device.
  /// Please use the `activeSubtitles()` method.
  bool active;
  String lang;
  String name;
  Uri url;

  TextTrack();

  TextTrack.fromMap(Map<String, dynamic> data) {
    this.id = data['id'] as int;
    this.active = data['active'];
    this.lang = data['lang'];
    this.name = data['name'];
    this.url = data['url'] != null
      ? Uri.parse(data['url'])
      : null;
  }

  Map<String, dynamic> toMap() => {
      'id': this.id,
      'active': this.active,
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
