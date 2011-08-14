package com.example.nwsr

import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.XML

import java.io.InputStream

// Etag/last-modified in HTTP header
// set "updated" based on user refresh, no date parsing

class NotFeedException() extends Exception() { }

class FeedData (
  val title: String,
  val link: String
)
{
}

// Could be written really easily in Scala, but there's a bug in Android
//   (involving SAXNotSupportedException) that wasn't fixed until 2.2
// Need handling for malformed RSS/Atom
object FeedData {
  def parseRSS(channel: Node): FeedData = {
    /*
    val stories = for (story <- channel \ "item")
                  yield Story((story \ "title").text, (story \ "link").text)
    */
    new FeedData((channel \ "title").text,
                 (channel \ "link").text)
  }

  def parseAtom(feed: Elem, nodes: NodeSeq): FeedData = {
    /*
    val stories = for (story @ <entry>{_*}</entry> <- nodes)
                  yield Story((story \ "title").text,
                              ((story \ "link").head \ "@href").text)
    */
    new FeedData((feed \ "title").text,
                 ((feed \ "link").head \ "@href").text)
  }

  def parseFeed(input: InputStream): FeedData = {
    val document = XML.load(input)
    document match {
      case <rss>{channel}</rss> => parseRSS(channel)
      case <feed>{inner @ _*}</feed> => parseAtom(document, inner)
      case _ => throw new NotFeedException()
    }
  }
}

