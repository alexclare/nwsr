package com.example.nwsr

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

// Etag/last-modified in HTTP header
// set "updated" based on user refresh, no date parsing

class NotFeedException() extends Exception() { }

class FeedData {
  var title: String = _
  var link: String = _
}

/* Could be written really easily in Scala, but there's a bug in Android
   involving the SAX parser it depends on that wasn't fixed until 2.2

   Using the DOM interface instead
*/

object FeedData {
  def parseFeed(input: InputStream): FeedData = {
    val result = new FeedData()
    val doc = DocumentBuilderFactory.newInstance
      .newDocumentBuilder.parse(input)
    val root = doc.getDocumentElement
    root.getTagName match {
      case "rss" => {
        val children = root.getFirstChild.getChildNodes
        for (x <- 0 until children.getLength; node = children.item(x)) {
          if (node.getNodeName == "title")
            result.title = node.getFirstChild.getNodeValue
          else if (node.getNodeName == "link")
            result.link = node.getFirstChild.getNodeValue
        }
      }
      case "feed" => {
        val children = root.getChildNodes
        for (x <- 0 until children.getLength; node = children.item(x)) {
          if (node.getNodeName == "title")
            result.title = node.getFirstChild.getNodeValue
          else if (node.getNodeName() == "link")
            result.link = node.getAttributes
              .getNamedItem("href").getNodeValue
            // be careful here - might not have the right value in qName, in
            //   which case I'd have to use another getValue function
        }
      }
      case _ => throw new NotFeedException()
    }
    result
  }
}

