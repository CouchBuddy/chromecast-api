package io.github.couchbuddy.chromecast_api

import android.content.Context
import android.net.Uri
import androidx.annotation.NonNull
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.RemoteMediaPlayer
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.common.images.WebImage
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.plugin.common.EventChannel
import java.lang.Exception

var context: Context? = null
var castStreamHandler: CastStreamHandler? = null
var mediaStreamHandler: MediaStreamHandler? = null

/** ChromecastApiPlugin */
class ChromecastApiPlugin: FlutterPlugin, ActivityAware, MethodCallHandler {
  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext

    val channel = MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "chromecast_api")
    channel.setMethodCallHandler(ChromecastApiPlugin());

    castStreamHandler = CastStreamHandler()
    mediaStreamHandler = MediaStreamHandler()

    EventChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "cast_state_event").apply {
      setStreamHandler(castStreamHandler)
    }

    EventChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "media_state_event").apply {
      setStreamHandler(mediaStreamHandler)
    }
  }

  override fun onAttachedToActivity(activityPluginBinding: ActivityPluginBinding) {
    context = activityPluginBinding.activity

    castStreamHandler?.updateState()
    mediaStreamHandler?.updateState()
    mediaStreamHandler?.addSessionManagerListener()
  }

  override fun onDetachedFromActivityForConfigChanges() {
    mediaStreamHandler?.removeSessionManagerListener()
  }

  override fun onReattachedToActivityForConfigChanges(activityPluginBinding: ActivityPluginBinding) {
    context = activityPluginBinding.activity

    castStreamHandler?.updateState()
    mediaStreamHandler?.updateState()
    mediaStreamHandler?.addSessionManagerListener()
  }

  override fun onDetachedFromActivity() {
    mediaStreamHandler?.removeSessionManagerListener()
  }

  // This static function is optional and equivalent to onAttachedToEngine. It supports the old
  // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
  // plugin registration via this function while apps migrate to use the new Android APIs
  // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
  //
  // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
  // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
  // depending on the user's project. onAttachedToEngine or registerWith must both be defined
  // in the same class.
  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      context = registrar.activeContext()

      val channel = MethodChannel(registrar.messenger(), "chromecast_api")
      channel.setMethodCallHandler(ChromecastApiPlugin())

      EventChannel(registrar.messenger(), "cast_state_event").apply {
        setStreamHandler(CastStreamHandler())
      }

      EventChannel(registrar.messenger(), "media_state_event").apply {
        setStreamHandler(MediaStreamHandler())
      }
    }
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "activateSubtitles" -> activateSubtitles((call.arguments() as Int).toLong())
      "loadMedia" -> loadMedia(call)
      "playOrPause" -> playOrPause()
      "showCastDialog" -> showCastDialog()
      else -> result.notImplemented()
    }
  }

  private val castContext: CastContext?
    // Note: it raises exceptions when the current device does not have Google Play service.
    get() = try {
      CastContext.getSharedInstance(context!!)
    } catch (error: Exception) {
      null
    }

  // Shows the Chromecast dialog.
  private fun showCastDialog() {
    castContext?.let {
        it.sessionManager?.currentCastSession?.let {
            MediaRouteControllerDialog(context!!, R.style.DefaultCastDialogTheme).show()
        } ?: run {
            MediaRouteChooserDialog(context!!, R.style.DefaultCastDialogTheme).apply {
                routeSelector = it.mergedSelector
                show()
            }
        }
    }
  }

  private fun loadMedia(call: MethodCall) {
      val movieMetadata = MediaMetadata(call.argument("type") ?: 0)

      movieMetadata.putString(MediaMetadata.KEY_TITLE, call.argument("title"))

      if (call.argument("type") as? Int == MediaMetadata.MEDIA_TYPE_TV_SHOW) {
          movieMetadata.putString(MediaMetadata.KEY_SERIES_TITLE, call.argument("seriesTitle"))
          movieMetadata.putInt(MediaMetadata.KEY_SEASON_NUMBER, call.argument("season") ?: 0)
          movieMetadata.putInt(MediaMetadata.KEY_EPISODE_NUMBER, call.argument("episode") ?: 0)
      }

      val images: ArrayList<String>? = call.argument("images")
      images?.map { movieMetadata.addImage(WebImage(Uri.parse(it))) }

      val tracks: ArrayList<MediaTrack> = ArrayList()

      val subtitles: ArrayList<Map<String, Any>>? = call.argument("subtitles")
      subtitles?.forEach {
          tracks.add(
              MediaTrack.Builder((it["id"] as Int).toLong(), MediaTrack.TYPE_TEXT)
                  .setName(it["name"] as String)
                  .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                  .setContentId(it["url"] as String)
                  .setLanguage(it["lang"] as String)
                  .setContentType("text/vtt")
                  .build()
          )
      }

      val mediaInfo: MediaInfo = MediaInfo.Builder(call.argument("url"))
          .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
          .setMetadata(movieMetadata)
          .setMediaTracks(tracks)
          .build()

      val remoteMediaClient: RemoteMediaClient = castContext?.sessionManager?.currentCastSession!!.remoteMediaClient
      val mediaRequest: MediaLoadRequestData = MediaLoadRequestData.Builder()
          .setMediaInfo(mediaInfo)
          .setActiveTrackIds(if (!subtitles.isNullOrEmpty()) longArrayOf((subtitles[0]["id"] as Int).toLong()) else longArrayOf())
          .build()

      remoteMediaClient.load(mediaRequest)
  }

  private fun playOrPause () {
    val remoteMediaClient: RemoteMediaClient = castContext?.sessionManager?.currentCastSession!!.remoteMediaClient

    when (remoteMediaClient.playerState) {
      MediaStatus.PLAYER_STATE_PAUSED -> remoteMediaClient.play()
      MediaStatus.PLAYER_STATE_PLAYING -> remoteMediaClient.pause()
    }
  }

  private fun activateSubtitles(subsId: Long) {
      val remoteMediaClient: RemoteMediaClient = castContext?.sessionManager?.currentCastSession!!.remoteMediaClient
      remoteMediaClient.setActiveMediaTracks(longArrayOf(subsId))
          .setResultCallback { mediaChannelResult: RemoteMediaClient.MediaChannelResult ->
          if (!mediaChannelResult.status.isSuccess) {
              println("Failed to activate subtitles: ${mediaChannelResult.status.statusCode}")
          }
      }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
  }
}

class CastStreamHandler : EventChannel.StreamHandler {

    private var lastState = CastState.NO_DEVICES_AVAILABLE
    private var eventSink: EventChannel.EventSink? = null
    private val castStateListener = CastStateListener { state ->
      lastState = state
      eventSink?.success(state)
    }

    private val castContext: CastContext?
      get() = try {
        CastContext.getSharedInstance(context!!)
      } catch (error: Exception) {
        null
      }

    override fun onListen(p0: Any?, sink: EventChannel.EventSink?) {
      eventSink = sink
      castContext?.addCastStateListener(castStateListener)
      lastState = castContext?.castState ?: CastState.NO_DEVICES_AVAILABLE
      eventSink?.success(lastState)
    }

    override fun onCancel(p0: Any?) {
      castContext?.removeCastStateListener(castStateListener)
      eventSink = null
    }

    fun updateState() {
      lastState = castContext?.castState ?: CastState.NO_DEVICES_AVAILABLE
      eventSink?.success(lastState)
    }
}

class MediaStreamHandler : EventChannel.StreamHandler {

    private var lastState: Map<String, Any>? = null
    private var eventSink: EventChannel.EventSink? = null

    private val castContext: CastContext?
      get() = try {
        CastContext.getSharedInstance(context!!)
      } catch (error: Exception) {
        null
      }

    private var sessionManagerListener = object: SessionManagerListener<Session> {
      override fun onSessionStarting(session: Session) {
      }

      override fun onSessionStarted(session: Session, sessionId: String) {
        if (castContext?.sessionManager?.currentCastSession != null) {
          remoteMediaClient = castContext?.sessionManager?.currentCastSession!!.remoteMediaClient
          remoteMediaClient?.registerCallback(callback)
        }
      }

      override fun onSessionStartFailed(session: Session, err: Int) {
      }

      override fun onSessionResuming(session: Session, p1: String) {
      }

      override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
        if (castContext?.sessionManager?.currentCastSession != null) {
          remoteMediaClient = castContext?.sessionManager?.currentCastSession!!.remoteMediaClient
          remoteMediaClient?.registerCallback(callback)
        }
      }

      override fun onSessionResumeFailed(p0: Session, p1: Int) {
      }

      override fun onSessionSuspended(p0: Session, p1: Int) {
      }

      override fun onSessionEnded(session: Session, error: Int) {
      }

      override fun onSessionEnding(session: Session) {
      }
    }

    fun addSessionManagerListener() {
      castContext?.sessionManager!!.addSessionManagerListener(sessionManagerListener)
    }

    fun removeSessionManagerListener() {
      castContext?.sessionManager!!.removeSessionManagerListener(sessionManagerListener)
    }

    private var remoteMediaClient: RemoteMediaClient? = null

    private var callback = object: RemoteMediaClient.Callback() {
      override fun onStatusUpdated() {
        val metadata = remoteMediaClient?.mediaInfo?.metadata
      // if (call.argument("type") as? Int == MediaMetadata.MEDIA_TYPE_TV_SHOW)
        eventSink?.success(if (metadata != null) mapOf(
          "episode" to metadata?.getInt(MediaMetadata.KEY_EPISODE_NUMBER),
          "images" to metadata?.images?.map { it.url.toString() },
          "season" to metadata?.getInt(MediaMetadata.KEY_SEASON_NUMBER),
          "seriesTitle" to metadata?.getString(MediaMetadata.KEY_SERIES_TITLE),
          "subtitles" to remoteMediaClient?.mediaInfo?.mediaTracks?.map {
            mapOf("id" to it.id, "name" to it.name, "lang" to it.language)
          },
          "title" to metadata?.getString(MediaMetadata.KEY_TITLE),
          "type" to metadata?.getMediaType(),
          "url" to remoteMediaClient?.mediaInfo?.contentId
        ) else null)
      }
    }

    override fun onListen(p0: Any?, sink: EventChannel.EventSink?) {
      eventSink = sink

      if (castContext?.sessionManager?.currentCastSession != null) {
        remoteMediaClient = castContext?.sessionManager?.currentCastSession!!.remoteMediaClient
        remoteMediaClient?.registerCallback(callback)
      }
    }

    override fun onCancel(p0: Any?) {
      if (castContext?.sessionManager?.currentCastSession != null) {
        remoteMediaClient?.unregisterCallback(callback)
      }
      eventSink = null
    }

    fun updateState() {
      // lastState = remoteMediaClient?.mediaInfo
      // eventSink?.success(remoteMediaClient?.mediaInfo?.metadata)
    }
}
