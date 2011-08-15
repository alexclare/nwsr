package com.example.nwsr

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

  def extractStory(node: Node, feedType: FeedType): Story = {
    var title = ""
    var link = ""
    foreach(node.getChildNodes) {
      (child: Node) =>
        if (child.getNodeName == "title")
          title = child.getFirstChild.getNodeValue
        else if (child.getNodeName == "link")
          link = feedType match {
            case RSSFeed => child.getFirstChild.getNodeValue
            case AtomFeed => child.getAttributes
                               .getNamedItem("href").getNodeValue
          }
    }
    Story(title, link)
  }

  def parseFeed(input: InputStream): FeedData = {
    val result = new FeedData()
    val doc = DocumentBuilderFactory.newInstance
      .newDocumentBuilder.parse(input)
    val root = doc.getDocumentElement
    root.getTagName match {
      case "rss" => {
        val primary = extractStory(root.getFirstChild, RSSFeed)
        result.title = primary.title
        result.link = primary.link
        foreach(root.getElementsByTagName("item")) {
          (item:Node) =>
          result.stories = extractStory(item, RSSFeed) :: result.stories
        }
      }
      case "feed" => {
        val primary = extractStory(root, AtomFeed)
        result.title = primary.title
        result.link = primary.link
        foreach(root.getElementsByTagName("entry")) {
          (entry:Node) =>
          result.stories = extractStory(entry, AtomFeed) :: result.stories
        }
      }
      case _ => throw new NotFeedException()
    }
    result
  }
}

