package refugee;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;

import sim.field.continuous.Continuous2D;
import sim.field.geo.GeomVectorField;
import sim.field.grid.SparseGrid2D;
import sim.field.network.Edge;
import sim.field.network.Network;
import sim.io.geo.ShapeFileImporter;
import sim.util.Bag;
import sim.util.Int2D;
import sim.util.geo.MasonGeometry;
import net.sf.csv4j.*;

class MigrationBuilder {
    public static Migration migrationSim;
    
    private static HashMap<Integer, ArrayList<Double>> age_dist;

    public static HashSet<Geometry> removeGeometry = new HashSet<Geometry>();
    public static HashSet<LineString> allLineStrings = new HashSet<LineString>();
	//initialize world

	public static void initializeWorld(Migration sim, String popPath, String adminPath, String ageDistPath){
	/*
	 *     private double distance;
    private MigrationBuilder.Node start;
    private MigrationBuilder.Node end;
    private double speed;
    private int population;
    private double cost;
    private double transportLevel; 
    private double deaths; 
	 */
		migrationSim = sim;
	    age_dist = new HashMap<Integer, ArrayList<Double>>();
		String[] cityAttributes = {"NAME", "ID", "ORIG", "POP", "QUOTA", "VIOL", "ECON", "FAMILY"};
		String[] roadAttributes = {"SPEED", "POP", "COST", "TLEVEL", "DEATHS"};
		
        //age_dist = new HashMap<Integer, ArrayList<Double>>();
		migrationSim.world_height = 9990;
		migrationSim.world_width = 9390;

		migrationSim.roadNetwork = new Network();
		migrationSim.allRoadNodes = new SparseGrid2D(sim.world_width, sim.world_height);

		migrationSim.roadLinks = new GeomVectorField(sim.world_width, sim.world_height);
	    Bag roadAtt = new Bag(roadAttributes);
	    
	    GeomVectorField cities_vector = new GeomVectorField();
	    migrationSim.cityGrid = new SparseGrid2D(sim.world_width, sim.world_height);
	    Bag cityAtt = new Bag(cityAttributes);
	    
	    try{
	    String[] files = {Parameters.ROAD_PATH, Parameters.CITY_PATH};//shapefiles
	    Bag[] attfiles = {roadAtt, cityAtt};
	    GeomVectorField[] vectorFields = {migrationSim.roadLinks, cities_vector};
	    readInShapefile(files, attfiles, vectorFields);//read in attributes
	    InputStream inputStream = new FileInputStream("");//COMMENT OUT to change inputter
	    }
	    catch(FileNotFoundException e)
	    {
	    	e.printStackTrace();
	    }
	    makeCities(cities_vector, migrationSim.cityGrid, migrationSim.cities);
	    extractFromRoadLinks(migrationSim.roadLinks, migrationSim);
	    //read in structures
        addCitiesandRefugees(popPath, adminPath);
	}
	
    public static class Node
    {
        public Int2D location; 

        ArrayList<Edge> links;
        double weightOnLineString;//measures the weight on the line string from 0
        public HashSet<LineString> lineStrings = new HashSet<LineString>();
        public int index;
        public Node(Int2D l)
        {
            location = l;
            links = new ArrayList<Edge>();
        }

        public ArrayList<Edge> getLinks() {
            return links;
        }
        @Override
        public String toString()
        {
            return "(" + location.getX() + ", " + location.getY() + ")";
        }
        //
    }
    
    static void makeCities(GeomVectorField cities_vector, SparseGrid2D grid, Bag addTo){
    Bag cities = cities_vector.getGeometries();
    
    Envelope e = cities_vector.getMBR();
    double xmin = e.getMinX(), ymin = e.getMinY(), xmax = e.getMaxX(), ymax = e.getMaxY();
    int xcols = migrationSim.world_width - 1, ycols = migrationSim.world_height - 1;
    	for (int i = 0; i < cities.size(); i++)
    	{
    	MasonGeometry cityinfo= (MasonGeometry)cities.objs[i];
    	
    	 Point point = cities_vector.getGeometryLocation(cityinfo);
         double x = point.getX(), y = point.getY();
         int xint = (int) Math.floor(xcols * (x - xmin) / (xmax - xmin)), yint = (int) (ycols - Math.floor(ycols * (y - ymin) / (ymax - ymin))); // REMEMBER TO FLIP THE Y VALUE
         
    	String name = cityinfo.getStringAttribute("NAME");
    	int ID = cityinfo.getIntegerAttribute("ID");
    	int origin = cityinfo.getIntegerAttribute("ORIG");
    	int population = cityinfo.getIntegerAttribute("POP");
    	int quota = cityinfo.getIntegerAttribute("QUOTA");
    	double violence = cityinfo.getDoubleAttribute("VIOL");
    	double economy = cityinfo.getDoubleAttribute("ECON");
    	double familyPresence = cityinfo.getDoubleAttribute("FAMILY");
    	Int2D location = new Int2D(xint, yint);
    	
    	City city = new City(location, ID, origin, population, quota, violence, economy, familyPresence);
    	addTo.add(city);
        grid.setObjectLocation(city, location);
    	}
    }
    static void readInShapefile(String[] files, Bag[] attfiles, GeomVectorField[] vectorFields)
    {
        try
        {
            for(int i = 0; i < files.length; i++)
            {
            	Bag attributes = attfiles[i];
                String filePath = files[i];
                File file = new File(filePath);
                URL shapeURI = file.toURI().toURL();
                ShapeFileImporter.read(shapeURI, vectorFields[i], attributes);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
    
    private static void addCitiesandRefugees(String pop_file, String admin_file)
    {
        try
        {
           System.out.print("Adding cities ");
           for (Object c : migrationSim.cities){
        	   
        	   City city = (City)c;
            InputStream inputstream = new FileInputStream("");
           
            int currentPop = 0;//1,4,5,10,3,14,24
            while (true){
            	if (city.getOrigin() == 1){
            ArrayList<Refugee> r = createRefugeeFamily(city.getLocation(), city);
            if (currentPop + r.size() <= city.getQuota()){
            for (Refugee refugee: r){
            		city.addMember(refugee);
            		migrationSim.refugees.add(refugee);
            		migrationSim.schedule.scheduleRepeating(refugee);
            	}
            }
            else
            	break;
            	}
            
            }
           }
            
            
        }
        catch(FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }
    
    
    
    
    private static ArrayList<Refugee> createRefugeeFamily(Int2D location, City city)
    {

    	//generate family
    	int family = pickFamilySize(age_dist, city);
    	ArrayList<Refugee> refugeeFamily = new ArrayList<Refugee>(family);
    	for (int i = 0; i < family; i++){
    		
    	
        //first pick sex
        int sex;
        if(migrationSim.random.nextBoolean())
            sex = Constants.MALE;
        else
            sex = Constants.FEMALE;

        //now get age
        int age = 0; //pick_age(age_dist, City city);
        double finStatus = pick_fin_status();
        //
        Refugee refugee = new Refugee(location, finStatus, sex, age, null);

        refugeeFamily.add(refugee);
    }
    	
    	for (Refugee refugee: refugeeFamily){
    		ArrayList<Refugee> myFamily = refugeeFamily;
    		myFamily.remove(refugee);
    		refugee.setFamily(myFamily);
    	}
    	
    	return refugeeFamily;
    	
    	
    }
    
    private static int pick_age(HashMap<Integer, ArrayList<Double>> age_dist, int county_id)
    {
        double rand = migrationSim.random.nextDouble();
        //if(county_id == -9999)
           // county_id = Parameters.MIN_LIB_COUNTY_ID;
        ArrayList<Double> dist = age_dist.get(county_id);
        int i;
        for(i = 0; i < dist.size(); i++)
        {
            if(rand < dist.get(i))
                break;
        }
        int age = i*5 + migrationSim.random.nextInt(5);
        //System.out.println(age + " years");
        return age;
    }
    
    private static void setUpAgeDist(String age_dist_file)
    {
        try
        {
            // buffer reader for age distribution data
            CSVReader csvReader = new CSVReader(new FileReader(new File(age_dist_file)));
            csvReader.readLine();//skip the headers
            List<String> line = csvReader.readLine();
            while(!line.isEmpty())
            {
                //read in the county ids
                int county_id = NumberFormat.getNumberInstance(java.util.Locale.US).parse(line.get(0)).intValue();
                //relevant info is from 5 - 21
                ArrayList<Double> list = new ArrayList<Double>();
                //double sum = 0;
                for(int i = 5; i <= 21; i++)
                {
                    list.add(Double.parseDouble(line.get(i)));
                    //sum += Double.parseDouble(line.get(i));
                    //Use cumulative probability
                    if(i != 5)
                        list.set(i-5, list.get(i-5) + list.get(i-5 - 1));
                    //System.out.print(list.get(i-5));
                }
                //System.out.println("sum = " + sum);
                //System.out.println();
                //now add it to the hashmap
                age_dist.put(county_id, list);
                line = csvReader.readLine();
            }
        }
        catch(FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch(java.text.ParseException e)
        {
            e.printStackTrace();
        }
    }
    private static double pick_fin_status() {
		// TODO Auto-generated method stub
		return 0;
	}
	private static int pickFamilySize(HashMap<Integer, ArrayList<Double>> age_dist, City city) {
		// TODO Auto-generated method stub
		return 0;
	}
	static void extractFromRoadLinks(GeomVectorField roadLinks, Migration migrationSim)
    {
        Bag geoms = roadLinks.getGeometries();
        Envelope e = roadLinks.getMBR();
        double xmin = e.getMinX(), ymin = e.getMinY(), xmax = e.getMaxX(), ymax = e.getMaxY();
        int xcols = migrationSim.world_width - 1, ycols = migrationSim.world_height - 1;
        int count = 0;

        //allNetworks = new LinkedList<HashSet<LineString>>();

        // extract each edge
        for (Object o : geoms)
        {
            MasonGeometry gm = (MasonGeometry) o;
         	double speed = gm.getDoubleAttribute("SPEED");
         	double distance = gm.getDoubleAttribute("SHAPE_length");
         	double cost = gm.getDoubleAttribute("COST");
         	double transportlevel = gm.getDoubleAttribute("TLEVEL");
         	double deaths = gm.getDoubleAttribute("DEATHS");
         	
         	EdgeInfo edgeinfo = new EdgeInfo(speed, distance, cost, transportlevel, deaths);
         	
            if (gm.getGeometry() instanceof LineString)
            {
                count++;
                readLineString((LineString) gm.getGeometry(), xcols, ycols, xmin, ymin, xmax, ymax, migrationSim, edgeinfo);

            } else if (gm.getGeometry() instanceof MultiLineString)
            {
                MultiLineString mls = (MultiLineString) gm.getGeometry();
                for (int i = 0; i < mls.getNumGeometries(); i++)
                {
                    count++;
                    readLineString((LineString) mls.getGeometryN(i), xcols, ycols, xmin, ymin, xmax, ymax, migrationSim, edgeinfo);
                }
            }
//            if(count%10000 == 0)
//                System.out.println("# of linestrings = " + count);

        }

    }
    static void readLineString(LineString geometry, int xcols, int ycols, double xmin,
            double ymin, double xmax, double ymax, Migration migrationSim, EdgeInfo edgeinfo) {
    		
    		CoordinateSequence cs = geometry.getCoordinateSequence();
    			// iterate over each pair of coordinates and establish a link between
    			// them
    		 if(!allLineStrings.add(geometry)) //Uncomment for linestring trimming
    	            return;

    	        //linestring trimming: HashSet<LineString> curSet = new HashSet<LineString>();
    	        //curSet.add(geometry);
    	        //allNetworks.addFirst(curSet);
//    	        ListIterator<HashSet<LineString>> listIterator = allNetworks.listIterator();
//    	        listIterator.next();
//    	        int removeIndex = 0;
    		 Node oldNode = null; // used to keep track of the last node referenced
    	        Node oldNodeTrimmed = null; //used to keep track of last trimmed node referenced
    	        int trimmed_distance = 0;
    	        for (int i = 0; i < cs.size(); i++)
    	        {
    	            // calculate the location of the node in question
    	            double x = cs.getX(i), y = cs.getY(i);
    	            int xint = (int) Math.floor(xcols * (x - xmin) / (xmax - xmin)), yint = (int) (ycols - Math.floor(ycols * (y - ymin) / (ymax - ymin))); // REMEMBER TO FLIP THE Y VALUE

    	            if (xint >= migrationSim.world_width)
    	                continue;
    	            else if (yint >= migrationSim.world_height)
    	                continue;
    	         // find that node or establish it if it doesn't yet exist
    	            Bag ns = migrationSim.allRoadNodes.getObjectsAtLocation(xint, yint);
    	            Node n;
    	            if (ns == null)
    	            {
    	                n = new Node(new Int2D(xint, yint));
    	                n.lineStrings.add(geometry);
    	                n.index = i;
    	                migrationSim.allRoadNodes.setObjectLocation(n, xint, yint);
    	            }
    	            else //this means that we are connected to another linestring or this linestring
    	            {
    	                n = (Node) ns.get(0);
    	                
    	                //USE FOR NETWORK COLLAPSE
//    	                LineString searchFor = n.lineString;
//    	                ListIterator<HashSet<LineString>> nextIterator = allNetworks.listIterator();
//    	                //search for the other linestring
//    	                int temp = -1;
//    	                while(nextIterator.hasNext())
//    	                {
//    	                    HashSet<LineString> next = nextIterator.next();
//    	                    temp++;
//    	                    if(next.contains(searchFor))
//    	                    {
//    	                        if(next != curSet)
//    	                        {
//    	                            //add all from the previous hashset to this one
//    	                            next.addAll(curSet);
//    	                            curSet = next;
    	//
//    	                            //remove the earlier position
//    	                            //listIterator.remove();
//    	                            if(removeIndex != 0) {
//    	                                int john = 1;
//    	                                john++;
//    	                            }
//    	                            allNetworks.remove(removeIndex);
//    	                            if(removeIndex < temp)
//    	                                temp--;
//    	                            removeIndex = temp;
//    	                            //now reset the position of the iterator and change locations
//    	                            //removeIndex = nextIterator.nextIndex();
    	//
//    	                            if(removeIndex < 0 || !allNetworks.get(removeIndex).contains(geometry))
//    	                                System.out.println("ERROR ERROR ERROR ERROR!!!!!!!!!!!!!!!");
//    	                        }
//    	                        break;
//    	                    }
//    	                }
    	            }
    	            // attach the node to the previous node in the chain (or continue if
    	            // this is the first node in the chain of links)

    	            if (i == 0) { // can't connect previous link to anything
    	                oldNode = n; // save this node for reference in the next link
    	                continue;
    	            }

    	            //int weight = (int) n.location.distance(oldNode.location); // weight is just //TODO new weight
    	            // distance
    	            //add it to the thinned network if it is the first or last in the cs.

    	            if (oldNode == n) // don't link a node to itself
    	            {
    	                continue;
    	            }

    	            // create the new link and save it
    	            
    	            Edge e = new Edge(oldNode, n, edgeinfo);
    	            migrationSim.roadNetwork.addEdge(e);

    	            oldNode.links.add(e);
    	            n.links.add(e);
    	            n.weightOnLineString = trimmed_distance;
    	            oldNode = n; // save this node for reference in the next link
    	        }

    	        //if we haven't found any links the network should be null

    	    }
    	            
    
}

