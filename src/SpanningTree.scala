import akka.actor.ActorRef

trait SpanningTree {
  case class NodeState(color:Option[Int],master:Option[ActorRef],parent:Option[ActorRef], children:Option[List[ActorRef]])
  case class AbstractNodeState(parent:Int,children:List[Int])
  case object InfoRequest
  case object Seed
}