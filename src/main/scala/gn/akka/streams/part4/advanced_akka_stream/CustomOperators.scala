package gn.akka.streams.part4.advanced_akka_stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Random, Success}

// 4
object CustomOperators extends App {
  implicit val system: ActorSystem = ActorSystem("CustomOperators")
  import akka.stream.Materializer.matFromSystem

  // 1- a custom source which emits random numbers until canceled

  // step0- define the shape in the extend GraphStage abstract class
  class RandomNumberGenerator(max: Int) extends GraphStage[SourceShape[Int]] {
    // step1- define the port and the component-specific members
    val outPort: Outlet[Int] = Outlet[Int]("randomGenerator")
    val random = new Random()

    // step2- construct a new shape
    override def shape: SourceShape[Int] = SourceShape(outPort)

    // step3- create the logic
    // This is called when materializing the value
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        // step4- define mutable state - the core logic
        setHandler(
          outPort,
          new OutHandler {
            // This method will be called when there is demand from downstream
            override def onPull(): Unit = {
              // emit a new element
              val nextNumber = random.nextInt(max)
              // push it out of the outPort
              push(outPort, nextNumber)
            }
          }
        )
      }
  }

  // Creating a source based on a class
  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
//  randomGeneratorSource.runWith(Sink.foreach[Int](println))

  // 2- a custom sink that prints elements in batches of a given size

  class Batcher(batchSize: Int) extends GraphStage[SinkShape[Int]] {

    val inPort: Inlet[Int] = Inlet[Int]("batcher")

    override def shape: SinkShape[Int] = SinkShape[Int](inPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        override def preStart(): Unit = {
          pull(inPort) // first signal to demand upstream fo a new element
        }

        // mutable state (inside the 'GraphStageLogic' and never inside the 'Batcher' class)
        val batch = new mutable.Queue[Int]

        setHandler(
          inPort,
          new InHandler {
            // This method is called when the upstream wants to send me an element
            override def onPush(): Unit = {
              val nextElement = grab(inPort)
              batch.enqueue(nextElement)

              // Assuming some complex computation
              Thread.sleep(100)

              if (batch.size >= batchSize) {
                println(s"New batch: ${batch.dequeueAll(_ => true).mkString("[", ",", "]")}")
              }

              // need to signal some more demand upstream:
              pull(inPort) // sending a signal to whatever is connected to this port ==> send demand upstream
            }

            override def onUpstreamFinish(): Unit = {
              if (batch.nonEmpty) {
                println(s"New batch: ${batch.dequeueAll(_ => true).mkString("[", ",", "]")}")
                println(s"Stream finished.")
              }
            }
          }
        )
      }
  }

  val batcherSink = Sink.fromGraph(new Batcher(10))

//  randomGeneratorSource.to(batcherSink).run()
  // a chicken and egg problem: The 'randomGeneratorSource' is waiting for 'onPull' signal, and 'batcherSink' is waiting
  // for an 'onPush' signal. We need one of these components to send the first signal: Let it be the 'batcherSink.createLogic()'
  // using a special logic inside the Batcher Logic, known as 'preStart'

  // The backpressure of these components will happen automatically, so if the 'Batcher' takes a long time to run,
  // the backpressure signal will automatically be sent to the 'RandomNumberGenerator', and it will slow it down

  /*
  InHandlers interact with the upstream
    - onPush
    - onUpstreamFinish
    - onUpstreamFailure

  Input ports can check and retrieve elements
    - pull: signal demand
    - grab: take an element
    - cancel: tell upstream to stop
    - isAvailable
    - hasBeenPulled
    - isClosed

  OutHandlers interact with downstream
    - onPull
    - onDownstreamFinish
      (no onDownstreamFailure as I'll receive a cancel signal)

  Output ports can send elements
    - push: send an element
    - complete: finish the stream
    - fail
    - isAvailable
    - isClosed
   */

  /**
    * Exercise: A custom flow - a simple filter flowù
    * - 2 ports: an input port and an output port
    */

  class SimpleFilter[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {

    val inPort: Inlet[T] = Inlet[T]("filterIn")
    val outPort: Outlet[T] = Outlet[T]("filterOut")

    override def shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        setHandler(
          outPort,
          new OutHandler {
            override def onPull(): Unit = pull(inPort)
          }
        )

        setHandler(
          inPort,
          new InHandler {
            override def onPush(): Unit = {
              try {
                val nextElement = grab(inPort)
                if (predicate(nextElement)) {
                  push(outPort, nextElement) // pass it on
                } else {
                  pull(inPort) // ask for another element
                }
              } catch {
                case throwable: Throwable => failStage(throwable)
              }
            }
          }
        )
      }
  }

  val myFilter = Flow.fromGraph(new SimpleFilter[Int](_ > 50))
//  randomGeneratorSource.via(myFilter).to(batcherSink).run()

  // Materialized values in graph stages
  // Example: A flow that counts the number of elements that go through it

  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {
    val inPort: Inlet[T] = Inlet[T]("counterIn")
    val outPort: Outlet[T] = Outlet[T]("counterOut")

    // a val not a def this time, because we don't need to evaluate it each time
    override val shape: FlowShape[T, T] = FlowShape(inPort, outPort)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val promise = Promise[Int]
      val logic: GraphStageLogic = new GraphStageLogic(shape) {
        // setting mutable state
        var counter = 0
        setHandler(
          outPort,
          new OutHandler {
            override def onPull(): Unit = pull(inPort)

            override def onDownstreamFinish(): Unit = {
              promise.success(counter)
              super.onDownstreamFinish()
            }
          }
        )

        setHandler(
          inPort,
          new InHandler {
            override def onPush(): Unit = {
              // extract element
              val nextElement = grab(inPort)
              counter += 1
              // pass it on
              push(outPort, nextElement)
            }

            override def onUpstreamFinish(): Unit = {
              promise.success(counter)
              super.onUpstreamFinish()
            }

            override def onUpstreamFailure(ex: Throwable): Unit = {
              promise.failure(ex)
              super.onUpstreamFailure(ex)
            }
          }
        )
      }
      (logic, promise.future)
    }
  }

  val counterFlow = Flow.fromGraph(new CounterFlow[Int])

  val countFuuture: Future[Int] = Source(1 to 10)
  //    .map(x => if (x == 7) throw new RuntimeException("It's 7") else x)
    .viaMat(counterFlow)(Keep.right)
    .to(Sink.foreach[Int](x => if (x == 7) throw new RuntimeException("It's 7") else println(x)))
    // No exception will be thrown, because only the 'onDownstreamFinish' is defined for the 'outPort'/Sink
    //    .to(Sink.foreach[Int](println))
    .run()

  import system.dispatcher
  countFuuture.onComplete {
    case Success(count)     => println(s"The number of elements passed: $count")
    case Failure(exception) => println(s"Error occurred: $exception")
  }
}
