package refugee;

import sim.util.Int2D;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;



/**
 * This class is a wrapper class for an ArrayList that manages a locations and other
 * information
 */
public class Route {
	private List<Int2D> locations;// list of places this person needs to go
	private List<RoadInfo> edges;
	private double distance;
	private City start;
	private City end;
	private double speed;

	public Route(List<Int2D> locations, List<RoadInfo> edges, double distance, City start, City end, double speed) {
		this.locations = locations;
		this.edges = edges;
		this.distance = distance;
		this.start = start;
		this.end = end;
		this.speed = speed;
	}
	
	public Route(List<Int2D> locations, double distance, City start, City end, double speed) {
		this.locations = locations;
		//this.edges = edges;
		this.distance = distance;
		this.start = start;
		this.end = end;
		this.speed = speed;
	}

	/**
	 * @return next location to move, null if no more moves
	 */
	public Int2D getLocation(int index) {
		Int2D location = locations.get(index);
		return location;
	}
	
	public RoadInfo getEdge(int index) {
		RoadInfo edge = edges.get(index);
		return edge;
	}

	public int getLocIndex(Int2D loc) {
		return locations.indexOf(loc);
	}
	
	public int getEdgeIndex(RoadInfo edge) {
		return edges.indexOf(edge);
	}

	public double getTotalDistance() {
		return distance;
	}

	public int getNumSteps() {
		return locations.size();
	}

	public City getStart() {
		return start;
	}

	public City getEnd() {
		return end;
	}

	public Route reverse() {
		List<Int2D> reversedlocations = new ArrayList<Int2D>(locations.size());
		//List<RoadInfo> reversedEdges = new ArrayList<RoadInfo>(edges.size());
		for (int i = locations.size() - 1; i >= 0; i--){
			reversedlocations.add(locations.get(i));
			//reversedEdges.add(edges.get(i));
		}
		//return new Route(reversedlocations, reversedEdges, this.distance, this.end, this.start, speed);
		return new Route(reversedlocations,  this.distance, this.end, this.start, speed);
	}

	/*public void addToEnd(Int2D location) {
		// convert speed to correct units
		double speed = this.speed;

		speed *= Parameters.TEMPORAL_RESOLUTION;// now km per step

		// convert speed to cell block per step
		speed = Parameters.convertFromKilometers(speed);

		double dist = location.distance(locations.get(locations.size() - 1).getLoc());
		while (speed < dist) {
			locations.add(AStar.getPointAlongLine(locations.get(locations.size() - 1), location, speed / dist));
			dist = locations.get(locations.size() - 1).distance(location);
		}

		locations.add(location);
	}*/

	public double getSpeed() {
		return speed;
	}
}
