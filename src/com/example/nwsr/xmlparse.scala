package com.example.nwsr

import scala.collection.mutable.StringBuilder

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

import org.w3c.dom.Node
import org.w3c.dom.NodeList


object XmlUtils {
  def foreach(list: NodeList)(fn: (Node => Unit)) {
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

  // Part of the Android API versions 8+, with entity encoding fixed in 11
  def getTextContent(node: Node): String = {
    val result = new StringBuilder()
    foreach(node.getChildNodes) {
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
    foreach(node.getChildNodes) {
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
