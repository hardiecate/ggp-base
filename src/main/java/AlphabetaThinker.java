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

// Makes a alphabeta move

public class AlphabetaThinker extends StateMachineGamer {

	Player p;

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub

	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		StateMachine machine = getStateMachine();
		MachineState state = getCurrentState();
		Role role = getRole();
		return bestmove(role, state, machine);
	}

	public Move bestmove(Role role, MachineState state, StateMachine machine) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		List<List<Move>> myTurnLegalMoves = machine.getLegalJointMoves(state);
		Move move = myTurnLegalMoves.get(0).get(0);
		int score = 0;
		for (int i = 0; i < myTurnLegalMoves.size(); i++) {
			int alpha = 0;
			int beta = 100;
			List<Move> myTurnMove = myTurnLegalMoves.get(i);
			int result = minscore(role, myTurnMove.get(0), machine.getNextState(state, myTurnMove), machine, alpha, beta);
			if (result == 100) {
				return myTurnMove.get(0);
			}
			if (result > score) {
				score = result;
				move = myTurnMove.get(0);
			}
		}
		return move;
	}

	public int minscore(Role myRole, Move myAction, MachineState state, StateMachine machine, int alpha, int beta) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		if (machine.isTerminal(state)) {
			return machine.getGoal(state, myRole);
		}

		List<Role> opponents = findOpponents(myRole, machine);
		int score = 100;
		for (Role opponent: opponents) {
			List<Move> oppoLegalMoves = machine.getLegalMoves(state, opponent);
			for (Move oppoMove: oppoLegalMoves) {
				List<List<Move>> oppoTurnLegalMoves = machine.getLegalJointMoves(state, opponent, oppoMove);
				for (int i=0; i<oppoTurnLegalMoves.size(); i++){
					List<Move> legalTurn = oppoTurnLegalMoves.get(i);
					MachineState newstate = machine.getNextState(state, legalTurn);
					int result = maxscore(myRole, newstate, machine, alpha, beta);
					beta = Math.min(beta, result);
					if (beta <= alpha) return alpha;
				}
			}
		}
		return beta;
	}

	public List<Role> findOpponents(Role role, StateMachine machine) {
		List<Role> allRoles = machine.getRoles();
		List<Role> opponents = new ArrayList<Role>();
		for (Role testRole: allRoles) {
			if (testRole != role) opponents.add(testRole);
		}
		return opponents;
	}

	public int maxscore(Role role, MachineState state, StateMachine machine, int alpha, int beta) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		if (machine.isTerminal(state)) {
			return machine.getGoal(state, role);
		}
		List<List<Move>> myTurnLegalMoves = machine.getLegalJointMoves(state);
		Move move = myTurnLegalMoves.get(0).get(0);
		int score = 0;

		for (int i = 0; i < myTurnLegalMoves.size(); i++) {
			List<Move> myTurnMove = myTurnLegalMoves.get(i);
			int result = minscore(role, myTurnMove.get(0), machine.getNextState(state, myTurnMove), machine, alpha, beta);
			alpha = Math.max(alpha, result);
			if (alpha>=beta) return beta;
		}
		return alpha;
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
		return "AlphabetaThinker";
	}

}