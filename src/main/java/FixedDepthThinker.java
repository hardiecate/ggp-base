import java.util.ArrayList;
import java.util.List;

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
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

// Makes a fixed depth without heuristics move

public class FixedDepthThinker extends StateMachineGamer {

	Player p;
	int limit = 8;
	double w1,w2,last_player_score;
	boolean restrict;

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}


	// This is where the pre-game calculations are done.
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// StateMachine machine = getStateMachine();
		// machine.initialize(getMatch().getGame().getRules());
		// Role role = getRole();
		// MachineState state = machine.getInitialState();
		w1 = w2 = .5;
		last_opp_score = 100; 
		restrict = true;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		StateMachine machine = getStateMachine();
		MachineState state = getCurrentState();
		Role role = getRole();


		// FOR NOW, WE ARE STOPPING WITH ONE SECOND LEFT
		timeout = timeout - 1000;

		return bestmove(role, state, machine, timeout);
	}

	public Move bestmove(Role role, MachineState state, StateMachine machine, long timeOut) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {

		List<List<Move>> myTurnLegalMoves = machine.getLegalJointMoves(state);
		Move move = myTurnLegalMoves.get(0).get(0);
		int score = 0;
		int level = 0;
		for (int i = 0; i < myTurnLegalMoves.size(); i++) {
			List<Move> myTurnMove = myTurnLegalMoves.get(i);
			int result = minscore(role, myTurnMove.get(0), machine.getNextState(state, myTurnMove), machine, level, timeOut);
			if (result > score) {
				score = result;
				move = myTurnMove.get(0);
			}
		}
		return move;
	}

	public int minscore(Role myRole, Move myAction, MachineState state, StateMachine machine, int level, long timeOut) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		if (machine.isTerminal(state)) {
			return machine.getGoal(state, myRole);
		}

		List<Role> opponents = findOpponents(myRole, machine);
		//if (level >= limit) return oppoProximity(opponents, state, machine);
		int score = 100;
		for (Role opponent: opponents) {
			List<Move> oppoLegalMoves = machine.getLegalMoves(state, opponent);
			for (Move oppoMove: oppoLegalMoves) {
				long elapsed = System.currentTimeMillis();
			    if (elapsed >= timeOut) {
			        return score;
			    }
				List<List<Move>> oppoTurnLegalMoves = machine.getLegalJointMoves(state, opponent, oppoMove);
				for (int i=0; i<oppoTurnLegalMoves.size(); i++){
					List<Move> legalTurn = oppoTurnLegalMoves.get(i);
					MachineState newstate = machine.getNextState(state, legalTurn);
					int result = maxscore(myRole, newstate, machine, level + 1, timeOut);
					if (result < score) score = result;
				}
			}
		}
		return score;
	}

	public List<Role> findOpponents(Role role, StateMachine machine) {
		List<Role> allRoles = machine.getRoles();
		List<Role> opponents = new ArrayList<Role>();
		for (Role testRole: allRoles) {
			if (testRole != role) opponents.add(testRole);
		}
		return opponents;
	}

	public int maxscore(Role role, MachineState state, StateMachine machine, int level, long timeOut) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		if (machine.isTerminal(state)) {
			return machine.getGoal(state, role);
		}
		if (level >= limit) return evalfn(role, state, machine);
		List<List<Move>> myTurnLegalMoves = machine.getLegalJointMoves(state);
		int score = 0;

		for (int i = 0; i < myTurnLegalMoves.size(); i++) {
			List<Move> myTurnMove = myTurnLegalMoves.get(i);
			int result = minscore(role, myTurnMove.get(0), machine.getNextState(state, myTurnMove), machine, level, timeOut);
			if (result == 100) return result;
			if (result > score) {
				score = result;
			}
		}
		return score;
	}

	public int oppoProximity(List<Role> opponents, MachineState state, StateMachine machine) throws GoalDefinitionException {
		double score = 0.0;
		for (Role opponent: opponents) {
			score += machine.getGoal(state, opponent);
		}
		return 100 - (int)(score / opponents.size());
	}

	public int evalfn(Role role, MachineState state, StateMachine machine) throws MoveDefinitionException, GoalDefinitionException {
		//return 0; // for non heuristics
		double player = (double)machine.getGoal(state, role); // for simple goal proximity
		double opp = (double)oppoProximity(findOpponents(role, machine), state, machine);
		double f1 = (double)mobility(role, state, machine); // for mobility heuristic
		double f2 = (double)focus(role, state, machine); // for focus heuristic
		double w1,w2;
		//update strategy based on whether your score is going up or down from last move
		if (opp > player) {
			if (player < last_player_score) {
				restrict = !restrict;
			}
			last_player_score = player;
			if (restrict) {
				w2 = w1 * .5;
				w1 = 1.0 - w2;
			} else {
				w1 = w2 * .5;
				w2 = 1.0 - w1;
			}
		}
		return (int)(w1*f1 + w2*f2);
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
		return "FixedDepthThinker";
	}

}