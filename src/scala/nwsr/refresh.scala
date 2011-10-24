package com.aquamentis.nwsr

import android.app.AlarmManager
import android.app.IntentService
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.preference.PreferenceManager

import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException

import com.aquamentis.util.Feed
import com.aquamentis.util.NotFeedException
import com.aquamentis.util.RichDatabase._

class RefreshService extends IntentService ("NWSRRefreshService")
with SilentFeedRetriever {
  var db: NWSRDatabase = _

  override def onCreate() {
    super.onCreate()
    db = NWSRDatabase(this)
  }

  override def onHandleIntent(intent: Intent) {
    if (getSystemService(Context.CONNECTIVITY_SERVICE)
        .asInstanceOf[ConnectivityManager].getBackgroundDataSetting) {
      retrieveFeed(FeedAll)
    }
  }
}

class BootReceiver extends BroadcastReceiver {
  override def onReceive(context: Context, intent: Intent) {
    val manager = context.getSystemService(Context.ALARM_SERVICE)
      .asInstanceOf[AlarmManager]
    val service = PendingIntent.getService(
      context, 0, new Intent(context, classOf[RefreshService]),
      PendingIntent.FLAG_UPDATE_CURRENT)
    val interval = System.currentTimeMillis + 3600000
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    prefs.getString("feed_refresh_rate", "0") match {
      case "1" => manager.setInexactRepeating(
        AlarmManager.RTC_WAKEUP, interval,
        AlarmManager.INTERVAL_HOUR, service)
      case "2" => manager.setInexactRepeating(
        AlarmManager.RTC_WAKEUP, interval,
        AlarmManager.INTERVAL_HALF_DAY, service)
      case "3" => manager.setInexactRepeating(
        AlarmManager.RTC_WAKEUP, interval,
        AlarmManager.INTERVAL_DAY, service)
      case _ =>
    }
  }
}
