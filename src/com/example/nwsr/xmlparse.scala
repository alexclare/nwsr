import scala.xml.Elem
import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.Source
import scala.xml.XML

import org.xml.sax.SAXParseException

// Etag/last-modified in HTTP header
// set "updated" based on user refresh, no date parsing

object FeedParser {
  case class Story(title: String, link: String)
  case class Title(title: String, link: String)

  // Might need additional exception handling here (i.e. malformed RSS/Atom)
  def parseRSS(channel: Node): (Title, Seq[Story]) = {
    val title = Title((channel \ "title").text,
                      (channel \ "link").text)
    val stories = for (story <- channel \ "item")
                  yield Story((story \ "title").text, (story \ "link").text)
    (title, stories)
  }

  def parseAtom(feed: Elem, nodes: NodeSeq): (Title, Seq[Story]) = {
    val title = Title((feed \ "title").text,
                      ((feed \ "link").head \ "@href").text)
    val stories = for (story @ <entry>{_*}</entry> <- nodes)
                  yield Story((story \ "title").text,
                              ((story \ "link").head \ "@href").text)
    (title, stories)
  }

  def main(args: Array[String]) {
    if (args.length > 0) {
      val istream = Source.fromFile(args(0))
      var doc: Option[scala.xml.Elem] = None
      try {
        doc = Some(XML.load(istream))
      } catch {
        case ex: SAXParseException => doc = None
      }
      doc match {
        case Some(elems) => println(elems.label)
        case None =>
      }
      println(doc match {
        case Some(elems) => {
          elems match {
            case <rss>{channel}</rss> => parseRSS(channel)
            case <feed>{inner @ _*}</feed> => parseAtom(elems, inner)
            case _ => "nope"
          }
        }
        // Malformed/not XML
        case None => println("malformed or not XML")
      })
    }
  }
}
