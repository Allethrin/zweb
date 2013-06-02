import akka.actor._
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import akka.dispatch._
import scala.collection.mutable.ListMap

/* A SpanningTreeSupervisor controls an entire network of SpanningTreeNodes. 
 * This class is responsible for being the bridge/translator between the 
 * highest-level abstraction of the full simulation layer, and the lower-level 
 * workings of the actor model. The SpanningTreeSupervisor is the highest level 
 * of the actor hierarchy in this program.
 
  There are two ways to interact with a SpanningTreeSupervisor:
  
  1. Message the SpanningTreeSupervisor with a Go(Args) object
  ~~~~~~~~~~~~~~~~~~~~~~
  
  The SpanningTreeSupervisor, informed by the arguments provided with the Go object,
  sets up a new SpanningTreeNetwork and starts its computation.
  
  The arguments are of the format numNodes:Int, connections:List[(Int,Int)], seeds:List[Int]
  
  2. Message the SpanningTreeSupervisor with an InfoRequest object
  ~~~~~~~~~~~~~~~~~~~~~~
  
  The SpanningTreeSupervisor replies with a list of Option[AbstractNodeState] representing
  the state of each of the nodes in the network it controls. The Option has value None when
  the corresponding node fails to reply to the SpanningTreeSupervisor with its node state. 
  An AbstractNodeState is a NodeState ÔtranslatedÕ into the high-level abstraction that 
  the simulation layer understands.
 * */

class SpanningTreeSupervisor extends Actor with SpanningTree{
  import context.dispatcher
  implicit val timeout = Timeout(5 seconds)
  
  var nodes:Array[ActorRef] = Array()
  var args:Option[Args] = None
  
  
  def receive = {
    case Go(args) => {
      /* Instantiate nodes */
      (args nodes) map ((id:Int) => 
        nodes(id) = (context actorOf(Props[SpanningTreeNode],"nodeRef"+id)))
        
      /* Form connections */
      (args connections) foreach ((pair:(Int,Int)) => {
        nodes(pair _1) ! nodes(pair _2)
      })
      
      /* Seed computation */
      (args seeds) foreach ((id:Int) => {
        nodes(id) ! Seed
      })
             
      context become {
        /* Upon receiving an InfoRequest, replies with 
         * a List[Option[AbstractNodeState]] representing states
         * of all supervised nodes */
        
        /* Translation of the following block:
         * 
         * When SpanningTreeSupervisor receives an InfoRequest,
         * it assembles a message to reply to sender as follows:
         * 
         * For each node, send the node an InfoRequest and collect
         * all of the the Future[NodeState] responses in a list.
         * 
         * Attempt to extract the NodeStates, then process them into
         * AbstractNodeStates.
         * 
         * Reply with List[Option[AbstractNodeState]] representing the
         * state of all of the nodes in the network that responded.
         */
        
        /* TODO: This block maybe isn't done properly, it might
         * prematurely evaluate the futures and they will all turn
         * up as None
         */
        
        case InfoRequest => { sender ! (
            ((args nodes) map ((id:(Int)) => {
            (nodes(id) ? InfoRequest).mapTo[NodeState]})) map 
              ((state:scala.concurrent.Future[NodeState]) => {
                (state value) match {
                  case Some(state) => (state toOption) match {
                    case Some(state) => abstractNodeState(state)
                    case None => None}
                  case None => None}}))
        }
      }
      def abstractNodeState(state:NodeState):Option[AbstractNodeState] = {
        (state parent, state children) match {
          case (Some(parent), Some(children)) => 
            Some(AbstractNodeState(
             (nodes indexOf)(parent), children map ((k:ActorRef) => {(nodes indexOf)(k)})
            ))
          case (None, _) | (_,None) => None
        }
      }
    }
  }
}

case class Go(args:Args)
case class Args(numNodes:Int,connections:List[(Int,Int)],seeds:List[Int]) {
  val nodes = (0 to numNodes)
}