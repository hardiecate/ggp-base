package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/**
 * RandomGamer is a very simple state-machine-based Gamer that will always
 * pick randomly from the legal moves it finds at any state in the game.
 */
public final class RandomGamer extends SampleGamer
{
    /**
     * This function is called at the start of each round
     * You are required to return the Move your player will play
     * before the timeout.
     *
     */
    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        long start = System.currentTimeMillis();

        List<Move> moves = getStateMachine().findLegals(getRole(), getCurrentState());

        Move selection = (moves.get(new Random().nextInt(moves.size())));
        long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
    }
}