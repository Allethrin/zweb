import akka.actor._
import akka.event.Logging
import scala.collection.mutable.{ArrayBuffer,ListMap}
import scala.util.Random

/** A SpanningTreeNode is a single node that communicates with other 
 *  SpanningTreeNodes in a network to perform a distributed computation 
 *  of a spanning tree of the network.
 *  
 *  Each SpanningTreeNode in a network will have knowledge of its 
 *  parent node and its children in a spanning tree of the network,
 *  within a finite time after calculation starts.
 *  
 *  SpanningTreeNodes have only local knowledge; they learn of each 
 *  others' states and perform the spanning tree calculation exclusively
 *  by passing messages with their neighbors.
 *  
 *  Directions for use:
 *  ~~~~~~~~~~~~~~~~~~~~
 *  
 *  Instantiate using the standard Akka methods 
 *  within an ActorSystem or an ActorContext
 *  
 *  Form bidirectional communication link between nodes:
 *  
 *  	nodeRef2 ! nodeRef1    OR     nodeRef1 ! nodeRef2
 *  
 *  Start spanning tree computation rooted at a node:
 *  
 *  	nodeRef ! Seed
 *  
 *  Retrieve the state of a node (node replies with state):
 *  
 *  	nodeRef ! InfoRequest
 */

class SpanningTreeNode extends Actor with SpanningTree {
  val log = Logging(context.system, this)
  
  private[this] var others:ListMap[ActorRef,Option[NodeState]] = new ListMap
  private[this] var tree:Option[SubtreeIdentifier] = None
  private[this] var parent:ActorRef = self
  private[this] var children:ArrayBuffer[ActorRef] = new ArrayBuffer[ActorRef]
  
  def receive = { 
    /* THIS BLOCK HANDLES MESSAGES RECEIVED FROM OUTSIDE OF NETWORK */
    case other:ActorRef => other match {
      case this.self => {}
      case notSelf => {others += other -> None; other ! self}
    }
    
    case InfoRequest => sender ! myState
    case Seed => newAgent
    /*~~~~~~~~~ END OF OUTSIDE MESSAGE RECEIVAL BLOCK ~~~~~~~ */
    
    /* THIS BLOCK HANDLES STATE NOTIFICATIONS RECEIVED FROM NEIGHBORING NODES */
      case theirData:NodeState => {
        others += sender -> Some(theirData) /* Update beliefs about the sender's state */
        (tree, theirData color, theirData parent) match { /* Register/remove sender as child, if necessary */
          case (Some(myTree),Some(theirColor),Some(theirParent)) => {
            if(theirColor == (myTree color) && theirParent == self) {
              if(!(children contains(sender))) {children += sender}
              else {children -= sender}
            }
          }
          case anythingElse => {}
        }
      } /*~~~~~~~~ END OF STATE NOTIFICATION RECEIVAL BLOCK ~~~~~~~~~~~~~~~ */
      
        case their:MarkovAgent => {
          tree match {
            case None => {join;forward}
            case Some(myTree) => {
              myTree compare (their tree) match {
                case n:Int if n > 0 => {sender ! CloneAgent(myTree,their tree,self)}
                case n:Int if n == 0 => forward
                case n:Int if n < 0 => {parent ! CloneAgent(their tree, myTree,self);join;forward}
              }
            }
          }      
          def forward {((others toList)(Random nextInt(others size)) _1) ! their} /* Defines how to forward a MarkovAgent */
          def join { /* Defines how to join the subtree of a Markov agent */
            this tree = Some(their tree)
            parent = sender
            notifyOthers
          }
        }
        case their:CloneAgent => {
          their.start match {
            case this.self => {} /* Clone has completed traversal, destroy it */
            case anythingElse => {
              tree match {
                case None => {} /* Clone is in the wrong place, destroy it */
                case Some(myTree) => {
                  myTree match {
                    case their.target => {join;forward}
                    case their.tree => forward
                    case notTheirTarget => {} /* Clone is in the wrong place, destroy it */
                  }
                }
              }
            }
          }
          def forward {
            (parent::children.toList).find(others(_) == (their target)) match {
              case Some(receiver) => receiver ! their
              case None => parent ! their
            }
          }
          def join { /* Defines how to join subtree of a clone agent */
            this tree = Some(their tree)
            parent = sender
            notifyOthers
          }
        }
  }
  
  /**Sends a NodeState representing this node's state to every neighbor
   */
  private[this] def notifyOthers {others foreach(_._1!myState)}
  
  /**Returns a NodeState describing this node's state
   */
  private[this] def myState:NodeState = tree match { /* Knowledge of children is not currently shared but can be if needed*/
    case None => NodeState(None,None,Some(parent),None)
    case Some(myTree) => NodeState(Some(myTree color), Some(myTree root), Some(parent), None)
  }
  
  /**Creates a new random walk agent and immediately forwards it
   */
  private[this] def newAgent {
    ((others toList)(Random nextInt(others size)) _1) ! MarkovAgent(SubtreeIdentifier(Random nextInt,self))
  }
    
  private[this] case class SubtreeIdentifier(color:Int,root:ActorRef) extends Ordered[SubtreeIdentifier] {
    def compare(other:SubtreeIdentifier) = color compare(other color)
    def equals(other:SubtreeIdentifier) = (color == other.color && root == other.root)
  }
    
  private[this] case class MarkovAgent(tree:SubtreeIdentifier)
  private[this] case class CloneAgent(tree:SubtreeIdentifier,target:SubtreeIdentifier,start:ActorRef)
}