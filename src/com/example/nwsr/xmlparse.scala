package com.example.nwsr

import android.sax.RootElement
import android.sax.EndTextElementListener
import android.sax.StartElementListener
import android.util.Xml

import java.io.InputStream

import org.xml.sax.Attributes
import org.xml.sax.SAXException

// Etag/last-modified in HTTP header
// set "updated" based on user refresh, no date parsing

class NotFeedException() extends Exception() { }

class FeedData {
  var title: String = _
  var link: String = _
}

/* Could be written really easily in Scala, but there's a bug in Android
   involving the SAX parser it depends on that wasn't fixed until 2.2

   Using the android.sax interface instead
*/

// Need handling for malformed RSS/Atom, goes near the "xml.parse" bit
object FeedData {
  def parseRSS(input: InputStream): FeedData = {
    val result = new FeedData()
    val root = new RootElement("rss")
    val channel = root.getChild("channel")
    channel.getChild("title").setEndTextElementListener(
      new EndTextElementListener() {
        def end(body: String) {
          result.title = body
        }
    })
    channel.getChild("link").setEndTextElementListener(
      new EndTextElementListener() {
        def end(body: String) {
          result.link = body
        }
    })
    //val story = channel.getChild("item")
    /*
    val stories = for (story <- channel \ "item")
                  yield Story((story \ "title").text, (story \ "link").text)
    */
    Xml.parse(input, Xml.Encoding.UTF_8, root.getContentHandler())
    result
  }

  def parseAtom(input: InputStream): FeedData = {
    val result = new FeedData()
    val root = new RootElement("feed")
    root.getChild("title").setEndTextElementListener(
      new EndTextElementListener() {
        def end(body: String) {
          result.title = body
        }
    })
    root.getChild("link").setStartElementListener(
      new StartElementListener() {
        def start(attrs: Attributes) {
          // be careful here - might not have the right value in qName, in
          //   which case I'd have to use another getValue function
          result.link = attrs.getValue("href")
        }
    })
    /*
    val stories = for (story @ <entry>{_*}</entry> <- nodes)
                  yield Story((story \ "title").text,
                              ((story \ "link").head \ "@href").text)
    */
    Xml.parse(input, Xml.Encoding.UTF_8, root.getContentHandler())
    result
  }

  def parseFeed(input: InputStream): FeedData = {
    var result: Option[FeedData] = None
    try {
      result = Some(parseRSS(input))
    } catch {
      // doesn't work; Expat exception instead
      case ex: SAXException => result = Some(parseAtom(input))
    }
    result match {
      case Some(data) => data
      case None => throw new SAXException()
    }
  }
}

