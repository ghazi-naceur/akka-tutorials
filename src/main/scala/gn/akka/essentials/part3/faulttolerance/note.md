// 2

## Introduction

### Actor instance
- has methods
- may have internal state

### Actor reference aka incarnation
- created with actorOf
- has mailbox and can receive messages 
- contains one actor instance
- contains a UUID

### Actor path
- may or may not have an ActorRef inside

## Actor Lifecycle

- Actors can be:
  * started: create a new ActorRef with a UUID at a given path 
  * suspended: the actorRef will enqueue but NOT process more messages
  * resumed: the actorRef will continue processing more messages
  * restarted: 
    It is trickier. At first, the actorRef will be suspended. After that, the actor 
    instance will be swapt in a number of steps:
     - The old instance calls a lifecycle method called "preRestart"
     - The actor instance is released, and a new instance comes to take its place
     - The new actor instance calls a method called "postRestart"
     - then the actor reference is resumed
    
    Internal state inside the actor instance is destroyed on restart, because the actor instance
    is completely swapt 
  * stopped: Stopping basically releases the actor reference which occupies a given actor path 
    inside the actor system:
    - the actor instance calls a method called "postStop"
    - All watching actors receive Terminated(ref)
   
   After stopping, another actor may be created at the same path. This new actor has a different
   UUID, so different ActorRef, so  all the messages enqueued in the old actor reference are lost
