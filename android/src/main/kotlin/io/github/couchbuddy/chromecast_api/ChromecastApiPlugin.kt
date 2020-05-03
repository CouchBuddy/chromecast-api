package io.github.couchbuddy.chromecast_api

import android.content.Context
import android.net.Uri
import androidx.annotation.NonNull
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.app.MediaRouteControllerDialog
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
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
public class ChromecastApiPlugin: FlutterPlugin, ActivityAware, MethodCallHandler {
  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.getApplicationContext()

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
    context = activityPluginBinding.getActivity()

    castStreamHandler?.updateState()
    mediaStreamHandler?.updateState()
  }

  override fun onDetachedFromActivityForConfigChanges() {
    // TODO: the Activity your plugin was attached to was
    // destroyed to change configuration.
    // This call will be followed by onReattachedToActivityForConfigChanges().
  }

  override fun onReattachedToActivityForConfigChanges(activityPluginBinding: ActivityPluginBinding) {
    context = activityPluginBinding.getActivity()

    castStreamHandler?.updateState()
    mediaStreamHandler?.updateState()
  }

  override fun onDetachedFromActivity() {
    // TODO: your plugin is no longer associated with an Activity.
    // Clean up references.
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
      val movieMetadata: MediaMetadata = MediaMetadata(call.argument("type") ?: 0)

      movieMetadata.putString(MediaMetadata.KEY_TITLE, call.argument("title"))

      if (call.argument("type") as? Int == MediaMetadata.MEDIA_TYPE_TV_SHOW) {
          movieMetadata.putString(MediaMetadata.KEY_SERIES_TITLE, call.argument("seriesTitle"))
          movieMetadata.putInt(MediaMetadata.KEY_SEASON_NUMBER, call.argument("season") ?: 0)
          movieMetadata.putInt(MediaMetadata.KEY_EPISODE_NUMBER, call.argument("episode") ?: 0)
      }

      val images: ArrayList<String>? = call.argument("images")
      images?.map { movieMetadata.addImage(WebImage(Uri.parse(it))) }

      val tracks: ArrayList<MediaTrack> = ArrayList<MediaTrack>()

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
          .setActiveTrackIds(if (!subtitles.isNullOrEmpty()) longArrayOf((subtitles[0]["id"] as Int).toLong() ?: 0) else longArrayOf())
          .build()

      remoteMediaClient.load(mediaRequest)
  }

  private fun activateSubtitles(subsId: Long) {
      val remoteMediaClient: RemoteMediaClient = castContext?.sessionManager?.currentCastSession!!.remoteMediaClient
      remoteMediaClient.setActiveMediaTracks(longArrayOf(subsId))
          .setResultCallback() { mediaChannelResult: RemoteMediaClient.MediaChannelResult ->
          if (!mediaChannelResult.getStatus().isSuccess()) {
              println("Failed to activate subtitles: ${mediaChannelResult.getStatus().getStatusCode()}")
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

    private var lastState: MediaInfo? = null
    private var eventSink: EventChannel.EventSink? = null

    private val castContext: CastContext?
      get() = try {
        CastContext.getSharedInstance(context!!)
      } catch (error: Exception) {
        null
      }

    private var remoteMediaClient: RemoteMediaClient? = null

    private var callback = object: RemoteMediaClient.Callback() {
      override fun onStatusUpdated() {
        lastState = remoteMediaClient?.getMediaInfo()
        eventSink?.success(lastState)
        println("Status updated: ${lastState?.getContentId()}");
      }
    }

    override fun onListen(p0: Any?, sink: EventChannel.EventSink?) {
      if (castContext?.sessionManager?.currentCastSession != null) {
        remoteMediaClient = castContext?.sessionManager?.currentCastSession!!.remoteMediaClient

        eventSink = sink
        remoteMediaClient?.registerCallback(callback)
        lastState = remoteMediaClient?.getMediaInfo()
        eventSink?.success(lastState)
      }
    }

    override fun onCancel(p0: Any?) {
      if (castContext?.sessionManager?.currentCastSession != null) {
        remoteMediaClient?.unregisterCallback(callback)
      }
      eventSink = null
    }

    fun updateState() {
      lastState = remoteMediaClient?.getMediaInfo()
      eventSink?.success(lastState)
    }
}
