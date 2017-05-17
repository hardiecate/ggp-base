import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.ggp.base.apps.player.Player;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

// Makes a fixed depth without heuristics move

public class MonteCarloSearchThinker extends StateMachineGamer {

	Player p;
	int limit = 1;
	double w1 = .5;
	double w2 = .5;
	double last_player_score = 0;
	boolean restrict = true;
	int timeoutPadding = 2000;
	boolean wasTimedOut = false;
	int mcsCount = 100;
	long returnBy;
	Move bestSoFar = null;
	StateMachine machine = null;
	Role myRole = null;


	@Override
	public StateMachine getInitialStateMachine() {
//		return new CachedStateMachine(new ProverStateMachine());
		return new CachedStateMachine(new PropnetStateMachine());

	}


	// This is where the pre-game calculations are done.
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

		machine = getStateMachine();
		machine.initialize(getMatch().getGame().getRules());
		myRole = getRole();
		machine.getInitialState();
	}


	public Move chooseMove(List<Integer> possibleScores, List<Move> possibleMoves) {

		// Choosing the move that correlates to the highest montecarlo score
		Move bestChoice = null;
		int highestScore = 0;
		int numMoves = possibleMoves.size();
		System.out.println("We had " + numMoves + " moves available.");
		System.out.println(possibleScores.toString());
		System.out.println();
		for (int i = 0; i < numMoves; i++) {
			if (possibleScores.get(i) > highestScore) {
				highestScore = possibleScores.get(i);
				bestChoice = possibleMoves.get(i);
			}
		}
		return bestChoice;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		wasTimedOut = false;
		System.out.println(limit);

		int calculationTime = 1000;
		returnBy = timeout - timeoutPadding;


		MachineState state = getCurrentState();
		long start = System.currentTimeMillis();
		List<Move> possibleMoves = machine.getLegalMoves(state, myRole);
		// Creates a list of the same size as possibleMoves, all populated with 0s
		List<Integer> possibleScores = new ArrayList<Integer>((Collections.nCopies(possibleMoves.size(), 0)));
		int numMoves = possibleMoves.size();
		if (numMoves == 1) {
			System.out.println("There was only one move to make!");
			return possibleMoves.get(0);

		}

		while (true) {
			// Setting the possible scores
			for (int i = 0; i < numMoves; i++) {
				if (returnBy - calculationTime < System.currentTimeMillis()) {
					return chooseMove(possibleScores, possibleMoves);
				}
				Random randomizer = new Random();
				Move aMove = possibleMoves.get(i);
				List<List<Move>> rounds = machine.getLegalJointMoves(state, myRole, aMove);
				int random = randomizer.nextInt(rounds.size());
				List<Move> randomRound = rounds.get(random);
				MachineState newstate = machine.getNextState(state, randomRound);
				int score = montecarlo(myRole, newstate, machine);
				possibleScores.set(i, score);
			}
		}

	}



	public int montecarlo(Role role, MachineState state, StateMachine machine) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		int total = 0;
		for (int i = 0; i < mcsCount; i++) {
			int charge = depthcharge(role, state, machine);
			total = total + charge;
		}
		return (int) (1.0*total/mcsCount);

	}

	public int depthcharge (Role role, MachineState state, StateMachine machine) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state)) {
			return machine.getGoal(state, role);
		}

	    if (System.currentTimeMillis() >= returnBy) {
	    	wasTimedOut = true;
	    	System.out.println("We timed out!");
	        return 0;
	    }

		Random randomizer = new Random();
		List<List<Move>> legalJointMoves = machine.getLegalJointMoves(state);
		int random = randomizer.nextInt(legalJointMoves.size());
		List<Move> randomRound = legalJointMoves.get(random);
		MachineState newstate = machine.getNextState(state, randomRound);
		return depthcharge(role, newstate, machine);
	}


	public int mobility(Role role, MachineState state, StateMachine machine) throws MoveDefinitionException {
		List<Move> actions = machine.getLegalMoves(state, role);
		List<Move> feasibles = machine.findActions(role);
		return (int)((double)actions.size()/(double)feasibles.size() * 100);
	}

	public int focus(Role role, MachineState state, StateMachine machine) throws MoveDefinitionException {
		return 100 - mobility(role, state, machine);
	}


	@Override
	public void stateMachineStop() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub

	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "MonteCarloSearchThinker";
	}

}