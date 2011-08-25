package com.example.nwsr

import scala.collection.mutable.StringBuilder
import scala.collection.mutable.ListBuffer

import java.io.InputStream
import java.net.URL
import java.net.HttpURLConnection
import javax.xml.parsers.DocumentBuilderFactory

import org.w3c.dom.Node
import org.w3c.dom.NodeList


object Feed {
  def refresh(link: String, etag: Option[String],
              lastModified: Option[String]): Option[Feed] = {
    val url = new URL(if (link.startsWith("http://") ||
                          link.startsWith("https://")) link
                      else "http://" + link)
    val connection = url.openConnection().asInstanceOf[HttpURLConnection]
    etag match {
      case Some(e) => connection.setRequestProperty("If-None-Match", e)
      case None =>
    }
    lastModified match {
      case Some(l) => connection.setRequestProperty("If-Modified-Since", l)
      case None =>
    }
    try {
      val istream = connection.getInputStream()
      connection.getResponseCode match {
        case HttpURLConnection.HTTP_OK => {
          val etag = connection.getHeaderField("ETag")
          val lastModified = connection.getHeaderField("Last-Modified")
          val data = FeedParse.parseFeed(istream)
          Some(Feed(data.title, connection.getURL.toString, data.link,
                    if (etag == null) None else Some(etag),
                    if (lastModified == null) None else Some(lastModified),
                    data.stories.result()))
        }
        case HttpURLConnection.HTTP_NOT_MODIFIED => None
        case _ => throw new NotFeedException()
      }
    } finally {
      connection.disconnect()
    }
  }
}

case class Feed (
  title: String, link: String, displayLink: String, etag: Option[String],
  lastModified: Option[String], stories: List[Story])

case class Story(title: String, link: String)

class NotFeedException() extends Exception() { }

/** DOM-based RSS/Atom feed parser
 *    Could be written more easily in Scala, but there's a bug in Android
 *    involving the SAX parser it depends on that wasn't fixed until 2.2
 */
object FeedParse {
  sealed abstract class FeedType
  case object RSSFeed extends FeedType
  case object AtomFeed extends FeedType

  class RichNodeList(list: NodeList) {
    def foreach(fn: (Node => Unit)) {
      for (x <- 0 until list.getLength; node = list.item(x))
        fn(node)
    }
  }
  implicit def enrichNodeList(list: NodeList) = new RichNodeList(list)

  /** Extracts a node's text content, converting some XML entities it finds
   *
   *  Included in the Android API versions 8+, with entity encoding fixed in 11
   */
  def getTextContent(node: Node): String = {
    val result = new StringBuilder()
    node.getChildNodes.foreach {
      (child: Node) => result.append(child.getNodeType match {
        case Node.TEXT_NODE => child.getNodeValue
        case Node.ENTITY_REFERENCE_NODE => child.getNodeName match {
          case "#34" => "\""
          case "#38" => "&"
          case "#39" => "'"
          case "#60" => "<"
          case "#62" => ">"
          case _ => ""
        }
        case _ => ""
      })
    }
    result.toString
  }

  def extractStory(node: Node, feedType: FeedType): Story = {
    var title = ""
    var link = ""
    node.getChildNodes.foreach {
      (child: Node) => child.getNodeName match {
        case "title" => title = getTextContent(child)
        case "link" => link = feedType match {
          case RSSFeed => getTextContent(child)
          case AtomFeed => child.getAttributes
                             .getNamedItem("href").getNodeValue
          }
        case _ =>
      }
    }
    Story(title, link)
  }

  class FeedData {
    var title: String = _
    var link: String = _
    var stories: ListBuffer[Story] = ListBuffer.empty[Story]
  }

  def parseFeed(input: InputStream): FeedData = {
    val result = new FeedData()
    val doc = DocumentBuilderFactory.newInstance
      .newDocumentBuilder.parse(input)
    val root = doc.getDocumentElement
    val feedType = root.getTagName match {
      case ("rss"|"rdf:RDF") => RSSFeed
      case "feed" => AtomFeed
      case _ => throw new NotFeedException()
    }
    result.title = getTextContent(root.getElementsByTagName("title").item(0))
    result.link = feedType match {
      case RSSFeed => getTextContent(root.getElementsByTagName("link").item(0))
      case AtomFeed => root.getElementsByTagName("link").item(0).getAttributes
                        .getNamedItem("href").getNodeValue
    }
    root.getElementsByTagName(
      feedType match {
        case RSSFeed => "item"
        case AtomFeed => "entry"
      }).foreach {
      (story:Node) =>
        result.stories.append(extractStory(story, feedType))
    }
    result
  }
}
