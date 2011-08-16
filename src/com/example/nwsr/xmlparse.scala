package com.example.nwsr

import scala.collection.mutable.StringBuilder

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

import org.w3c.dom.Node
import org.w3c.dom.NodeList

// Etag/last-modified in HTTP header
// set "updated" based on user refresh, no date parsing

object XmlUtils {
  def foreach(list: NodeList)(fn:(Node => Unit)) {
    for (x <- 0 until list.getLength; node = list.item(x))
      fn(node)
  }
}
import XmlUtils._

class NotFeedException() extends Exception() { }

case class Story(title: String, link: String)
class FeedData {
  var title: String = _
  var link: String = _
  var stories: List[Story] = List()
}

sealed abstract class FeedType
case object RSSFeed extends FeedType
case object AtomFeed extends FeedType

/* Could be written really easily in Scala, but there's a bug in Android
   involving the SAX parser it depends on that wasn't fixed until 2.2

   Using the DOM interface instead
*/

object FeedData {

  def extractText(node: Node): String = {
    val result = new StringBuilder()
    foreach(node.getChildNodes) {
      (child: Node) => result.append(child.getNodeType match {
        case Node.TEXT_NODE => child.getNodeValue
        // handle HTML entities properly at some point
        case _ => " "
      })
    }
    result.toString
  }

  def extractStory(node: Node, feedType: FeedType): Story = {
    var title = ""
    var link = ""
    foreach(node.getChildNodes) {
      (child: Node) => child.getNodeName match {
        case "title" => title = extractText(child)
        case "link" => link = feedType match {
          case RSSFeed => child.getFirstChild.getNodeValue
          case AtomFeed => child.getAttributes
                             .getNamedItem("href").getNodeValue
          }
        case _ =>
      }
    }
    Story(title, link)
  }

  def parseFeed(input: InputStream): FeedData = {
    val result = new FeedData()
    val doc = DocumentBuilderFactory.newInstance
      .newDocumentBuilder.parse(input)
    val root = doc.getDocumentElement
    val feedType = root.getTagName match {
      case "rss" => RSSFeed
      case "feed" => AtomFeed
      case _ => throw new NotFeedException()
    }
    val primary = extractStory(
      feedType match {
        case RSSFeed => root.getFirstChild
        case AtomFeed => root
      }, feedType)
    result.title = primary.title
    result.link = primary.link
    foreach(root.getElementsByTagName(
      feedType match {
        case RSSFeed => "item"
        case AtomFeed => "entry"
      })) {
      (story:Node) =>
        result.stories = extractStory(story, feedType) :: result.stories
    }
    result
  }
}
