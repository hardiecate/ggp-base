import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

// Makes a fixed depth without heuristics move

public class MCTSThinker extends StateMachineGamer {

	Player p;
	int limit = 1;
	double w1 = .6;
	double w2 = .4;
	double last_player_score = 0;
	boolean restrict = true;
	int timeoutPadding = 2000;
	boolean wasTimedOut = false;
	int mcsCount = 100;
	long returnBy;
	Move bestSoFar = null;
	StateMachine machine = null;
	Role myRole = null;
	Map<MachineState, Integer> visited = new HashMap<MachineState, Integer>();
	Map<MachineState, Integer> utility = new HashMap<MachineState, Integer>();
	Map<MachineState, List<MachineState>> parents = new HashMap<MachineState, List<MachineState>>();

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
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

	private boolean timeLeft(int calculationTime){
		if (returnBy - calculationTime > System.currentTimeMillis()) {return true;}
		return false;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		wasTimedOut = false;
		System.out.println(limit);

		int calculationTime = 1000;
		returnBy = timeout - timeoutPadding;


		MachineState stateOrigin = getCurrentState();
		long start = System.currentTimeMillis();
		List<Move> possibleMoves = machine.getLegalMoves(stateOrigin, myRole);
		// Creates a list of the same size as possibleMoves, all populated with 0s
		List<Integer> possibleScores = new ArrayList<Integer>((Collections.nCopies(possibleMoves.size(), 0)));
		int numMoves = possibleMoves.size();
		if (numMoves == 1) {
			System.out.println("There was only one move to make!");
			return possibleMoves.get(0);

		}

		//Is this enough padding for our calculations?
		 while (timeLeft(calculationTime)) {
			 //STEPS ONE & TWO:
			 //Do selection routine to find our unvisited starting point
			 //Also within this method, add successors to the tree
			 MachineState state = select(stateOrigin);

			// Setting the possible scores
			for (int i = 0; i < numMoves; i++) {
				if (!(timeLeft(calculationTime))) {
  					return chooseMove(possibleScores, possibleMoves);
  				}

				//STEP THREE:
				//Same simulation routine w/ random action choices until
				//terminal state reached
				Random randomizer = new Random();
				Move aMove = possibleMoves.get(i);
				List<List<Move>> rounds = machine.getLegalJointMoves(state, myRole, aMove);
				int random = randomizer.nextInt(rounds.size());
				List<Move> randomRound = rounds.get(random);
				MachineState newstate = machine.getNextState(state, randomRound);
				int score = montecarlo(myRole, newstate, machine);
				possibleScores.set(i, score);

				//STEP FOUR:
				//Propogate back the value of the terminal state reached by the
				//depth charge within montecarlo
				if(timeLeft(calculationTime)) {
					backpropogate(state, score);
				}
			}
		}

		return chooseMove(possibleScores, possibleMoves);

	}

	public int oppoProximity(List<Role> opponents, MachineState state, StateMachine machine) throws GoalDefinitionException {
		double score = 0.0;
		for (Role opponent: opponents) {
			score += machine.getGoal(state, opponent);
		}
		return 100 - (int)(score / opponents.size());
	}


	public List<Role> findOpponents(Role role, StateMachine machine) {
		List<Role> allRoles = machine.getRoles();
		List<Role> opponents = new ArrayList<Role>();
		for (Role testRole: allRoles) {
			if (testRole != role) opponents.add(testRole);
		}
		return opponents;
	}

	public int evalfn(Role role, MachineState state, StateMachine machine) throws MoveDefinitionException, GoalDefinitionException {
		//return 0; // for non heuristics
		double player = (double)machine.getGoal(state, role); // for simple goal proximity
		double opp = (double)oppoProximity(findOpponents(role, machine), state, machine);
		double f1 = (double)mobility(role, state, machine); // for mobility heuristic
		double f2 = (double)focus(role, state, machine); // for focus heuristic

		//update strategy based on whether your score is going up or down from last move
		//if the opponent's score is higher than our player's score
		if (opp > player) {
			//if our score went down from the last round
			if (player < last_player_score) {
				System.out.println("Switching strategies!");;
				restrict = !restrict;
				if (restrict) {
					w2 = w1; // Not sure why we kept cutting this factor in half
					w1 = 1.0 - w2;
				} else {
					w1 = w2;
					w2 = 1.0 - w1;
				}
			}
			last_player_score = player;
		}

		// If we are really close to a goal state, emphasize that over the mobility/focus heuristics
		if (player > (w1*f1 + w2*f2)) {
			System.out.println("We are just gonna go for the goal.");
		}
		return (int)Math.max(player, (w1*f1 + w2*f2)); //(int)(w1*f1 + w2*f2);
	}

	public int selectfn(MachineState node, MachineState parent) throws MoveDefinitionException, GoalDefinitionException {
		return (int)((1.0*evalfn(myRole, node, machine)) + Math.sqrt(2*Math.log(visited.get(parent))))/(visited.get(node));
	}

	public MachineState select(MachineState node) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		if (!(visited.containsKey(node))) {return node;}
		List<MachineState> children = machine.getNextStates(node);

		// Update the parents list
		for (MachineState child : children) {
			if (parents.containsKey(child)) {
				if (!(parents.get(child).contains(node))) {
					List<MachineState> pars = parents.get(child);
					pars.add(node); // Updating list of this child's parents
					parents.put(node, pars); // Updating the global map

				}
			} else {
				List<MachineState> pars = new ArrayList<MachineState>();
				pars.add(node); // Updating list of this child's parents
				parents.put(node, pars); // Updating the global map

			}
		}

		// Return first child that has not been visited yet
		for (MachineState child : children) {
			if (!(visited.containsKey(child))) {
				return child;
			}
		}
	  int score = 0;
	  MachineState result = node;

	  // All children have been visited. Find highest score among children.
	  for (MachineState child: children) {
	      int newscore = selectfn(child, node);
	      if (newscore>score) {
	          score = newscore;
	          result=child;
	      }
	  }
	  return select(result);
  }

//	public boolean expand (MachineState node) throws MoveDefinitionException {
//		List<Move> actions = machine.getLegalMoves(node, myRole);
//		for (Move action: actions) {
//			List<Move> moves = machine.getLegalJointMoves(node, myRole, move);
//			moves.add(action);
//			MachineState newstate = machine.getNextState(node, moves);
//		}
//		return false;
//	}

	public boolean backpropogate(MachineState node,  int score) {
		if (visited.containsKey(node)) {
			visited.put(node, visited.get(node) + 1);
			utility.put(node, utility.get(node) + score);
			List<MachineState> pars = parents.get(node);
			for (MachineState parent : pars) {
				backpropogate(parent, score);
			}
		}
		return true;
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
		return "MCTSThinker";
	}

}