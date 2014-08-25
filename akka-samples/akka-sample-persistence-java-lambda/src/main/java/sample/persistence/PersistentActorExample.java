/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package sample.persistence;

//#persistent-actor-example

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.SnapshotOffer;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.io.Serializable;
import java.util.ArrayList;

import static java.util.Arrays.asList;

class Cmd implements Serializable {
    private final String data;

    public Cmd(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }
}

class Evt implements Serializable {
    private final String data;

    public Evt(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }
}

class ExampleState implements Serializable {
    private final ArrayList<String> events;

    public ExampleState() {
        this(new ArrayList<>());
    }

    public ExampleState(ArrayList<String> events) {
        this.events = events;
    }

    public ExampleState copy() {
        return new ExampleState(new ArrayList<>(events));
    }

    public void update(Evt evt) {
        events.add(evt.getData());
    }

    public int size() {
        return events.size();
    }

    @Override
    public String toString() {
        return events.toString();
    }
}

class ExamplePersistentActor extends AbstractPersistentActor {

    private ExampleState state = new ExampleState();

    public int getNumEvents() {
        return state.size();
    }

    @Override
    public String persistenceId() { return "sample-id-1"; }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveRecover() {
        return ReceiveBuilder.
            match(Evt.class, state::update).
            match(SnapshotOffer.class, ss -> state = (ExampleState) ss.snapshot()).build();
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receiveCommand() {
        return ReceiveBuilder.
            match(Cmd.class, c -> {
                final String data = c.getData();
                final Evt evt1 = new Evt(data + "-" + getNumEvents());
                final Evt evt2 = new Evt(data + "-" + (getNumEvents() + 1));
                persist(asList(evt1, evt2), (Evt evt) -> {
                    state.update(evt);
                    if (evt.equals(evt2)) {
                        context().system().eventStream().publish(evt);
                    }
                });
            }).
            match(String.class, s -> s.equals("snap"), s -> saveSnapshot(state.copy())).
            match(String.class, s -> s.equals("print"), s -> System.out.println(state)).
            build();
    }

}
//#persistent-actor-example

public class PersistentActorExample {
    public static void main(String... args) throws Exception {
        final ActorSystem system = ActorSystem.create("example");
        final ActorRef processor = system.actorOf(Props.create(ExamplePersistentActor.class), "processor-4-java8");
        processor.tell(new Cmd("foo"), null);
        processor.tell(new Cmd("baz"), null);
        processor.tell(new Cmd("bar"), null);
        processor.tell("snap", null);
        processor.tell(new Cmd("buzz"), null);
        processor.tell("print", null);

        Thread.sleep(1000);
        system.terminate();
    }
}
