package edu.uiuc.groupmessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileNotFoundException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.Scanner;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.ArrayList;
import java.io.RandomAccessFile;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;

import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import edu.uiuc.groupmessage.GroupMessageProtos.GroupMessage;
import edu.uiuc.groupmessage.GroupMessageProtos.Member;

class MemListNode {
    boolean AllDone;
    int phase = 1;
    private ArrayList<Boolean> JobDone;
    private ArrayList<Boolean> Jobf2Done;
    private PriorityQueue < job > queue;
	private Member currentMember;
	private Member portalMember;
	private Member heartbeatFrom;
	private Member heartbeatTo;
	private LinkedList< Member > memberList;
	private MemListServer server;
    private MapleMaster mserver;
    private Maplef2Master mf2server;
    private MapleWorker mworker;
    private Maplef2Worker mf2worker;
	private Timer heartbeatClientTimer;
	private Timer detectorTimer;
	private long heartbeatTimestamp;
	private final Logger LOGGER = Logger.getLogger(MemListNode.class.getName());

	MemListNode(Member current_member, Member portal_member) {
        
		currentMember = current_member;
		portalMember = portal_member;
		heartbeatFrom = null;
		heartbeatTo = null;
		memberList = new LinkedList< Member >();
		heartbeatClientTimer = null;
		detectorTimer = null;
		setHeartbeatTimestamp(-1);
		try {
			FileHandler fileTxt = new FileHandler(
					"/tmp/" + current_member.getIp() + "_" + current_member.getPort() + ".log");
			fileTxt.setFormatter(new SimpleFormatter());
			LOGGER.addHandler(fileTxt);
		} catch (IOException ex) {
			System.err.println("Log file cannot be created");
			System.exit(-1);
		}
		//LOGGER.addHandler(new ConsoleHandler());
	}

	public void runServer() {
		server = new MemListServer(this);
		server.start();
		startHeartbeatServer();
	}
    public MapleWorker getMapleWorker(){
        return mworker;
    }
    
    public MapleMaster getmserver(){
        return mserver;
    }
    public LinkedList< Member > getMemberList() {
		return memberList;
	}
    
    public PriorityQueue < job > getQ() {
		return queue;
	}
    
    public List< Boolean > getJobDone() {
        if (phase == 1)
            return JobDone;
        else
            return Jobf2Done;
	}
    
	public Member getCurrentMember() {
		return currentMember;
	}

	public Member getPortalMember() {
		return portalMember;
	}

	public Member getHearbeatFrom() {
		return heartbeatFrom;
	}

	public Member getHeartbeatTo() {
		return heartbeatTo;
	}

	synchronized public void setHeartbeatTimestamp(long timestamp) {
		heartbeatTimestamp = timestamp;
	}

	synchronized public long getHeartbeatTimestamp() {
		return heartbeatTimestamp;
	}

	public GroupMessage processMessage(GroupMessage msg) {
		switch(msg.getAction()) {
			case JOIN_REQUEST:
				return handleJoinRequest(msg.getTarget());
			case RESET_MEMBERLIST:
				handleResetMemberList(msg.getMemberList());
				break;
			case TARGET_JOINS:
				handleTargetJoins(msg.getTarget());
				break;
			case TARGET_LEAVES:
				handleTargetLeaves(msg.getTarget());
				break;
			case TARGET_FAILS:
				handleTargetFails(msg.getTarget());
				break;
			case TARGET_HEARTBEATS:
				handleHeartbeats(msg.getTarget());
				break;
            case MAPLE_REQUEST:
                handleInitMapleRequest(msg.getArgstrList());
                break;
            case MAPLE_WORK:
                return handleMapleWork(msg.getArgstrList());
            case MAPLE_WORK_DONE:
                if (phase == 2){
                    System.out.println("This is Ignored!!!");
                    return null;
                }
                handleMapleWorkDone(msg.getTarget(),msg.getArgstrList());
                break;
            case MAPLE_PHASE_TWO_SINGLE_WORK_DONE:
                if (AllDone){
                    System.out.println("This is Ignored!!!");
                    return null;
                }
                handleMapleWorkDone(msg.getTarget(),msg.getArgstrList());
                break;
            case MAPLE_PHASE_ONE_DONE:
                if (AllDone){
                    System.out.println("This is Ignored!!!");
                    return null;
                }
                handleMaplePhaseTwo(msg.getArgstrList());
                break;
            case MAPLE_F2_WORK:
                return handleMapleF2Work(msg.getArgstrList());
            case MAPLE_PHASE_TWO_DONE:
                AllDone = true;
                handleMapleComplete(msg.getArgstrList().get(0));
                break;
            case MAPLE_WORK_ABORT:
                return handleMapleWorkAbort(msg.getTarget());
		}
		return null;
	}
    public void handleMapleComplete(String prefix){
        // reset maple to ready state
        phase = 1;
        
        // remove all intermediate files
        LinkedList<String> workfiles = OprationSDFS("list",prefix,"");
        for (int i = 0; i < workfiles.size(); i++)
            OprationSDFS("delete",workfiles.get(i),"");
       
        System.out.println("-------Maple is Completely Done-----------");
    }
    
    public GroupMessage handleMapleF2Work(List< String > args){
        String prefix = args.get(0);
        String filename = args.get(1);
        String id = args.get(2);
        
        System.out.println("I receive job name "+ filename +", it's id "+ id);
        
        GroupMessage msg = GroupMessage.newBuilder()
        .setTarget(currentMember)
        .setAction(GroupMessage.Action.NODE_FREE)
        .build();
        
        mf2worker = new Maplef2Worker(this,prefix,filename,id);
        mf2worker.start();
        
        return msg;
    }
    
    public GroupMessage handleMapleWorkAbort(Member member){
        getMapleWorker().abort();
        getMapleWorker().interrupt();
        //getMapleWorker().stop();
        
        GroupMessage send_msg = GroupMessage.newBuilder()
        .setTarget(member)
        .setAction(GroupMessage.Action.NODE_ABORT)
        .build();
        
        return send_msg;
    }
    
    // To be done:
    // synchronize jobqueue when done with current work
    // need to ignore the reply from phase 1(his work is already done by someone else)
    public void handleMaplePhaseTwo(List< String > args){
        // request to abort current phase 1 jobs
        
        for ( int i = 0; i < getMemberList().size(); i++){
            GroupMessage send_msg = GroupMessage.newBuilder()
            .setTarget(currentMember)
            .setAction(GroupMessage.Action.MAPLE_WORK_ABORT)
            .build();
            
            Member member = getMemberList().get(i);
            GroupMessage rcv_msg = sendMessageWaitResponse(member, send_msg);
            if (rcv_msg.getAction() == GroupMessage.Action.NODE_ABORT){
                System.out.println("Member "+i+" abort.");
            } else {
                System.out.println("Member "+i+" does not abort.");
            }
        }
        
        // change phase to 2
        phase = 2;
        System.out.println("------------------------------------");
        System.out.println("Maple Phase two");
        
        String prefix = args.get(0);
        // get job list
        LinkedList<String> f2job = OprationSDFS("list",prefix,"");        
        trimKey(f2job);
        RemoveDuplicate(f2job);
        
        queue = new PriorityQueue<job>();
        for (int i = 0; i < f2job.size(); i++){
            System.out.println(f2job.get(i)+", id = "+i);
            queue.add(new job(f2job.get(i),0,i));
        }
        
        // Initial JobDone List
        Jobf2Done = new ArrayList<Boolean>();
        for (int i = 0; i < f2job.size(); i++)
            Jobf2Done.add(false);
        System.out.println("Jobf2Done has "+Jobf2Done.size()+" jobs.");
        System.out.println("queue has "+queue.size()+" jobs.");
        
        mf2server = new Maplef2Master(this,args.get(0),phase);
		mf2server.start();
    }
    
    
    public void DistributeJob(String prefix,int phase){
        
        LinkedList< Member > memberList = getMemberList();
        for (int i = 0 ; i < memberList.size(); i++)
		{
            // find proper job
            job TopJob = findjob(phase, prefix);
            if (TopJob == null)
                return;
            
            Member member = memberList.get(i);
            GroupMessage send_msg = null;
            if (phase == 1){
                //System.out.println("Distribute Maple Job Message in Phase 1!!!!!!!!!!!!!");
                send_msg = GroupMessage.newBuilder()
                .setTarget(member)
                .addArgstr(prefix)// pass prefix
                .addArgstr(TopJob.getname()) // pass filename
                .addArgstr(Integer.toString(TopJob.getid()))// pass job id
                .setAction(GroupMessage.Action.MAPLE_WORK)
                .build();
            } else if (phase == 2){
                //System.out.println("Distribute Maple Job Message in Phase 2!!!!!!!!!!!!!");
                send_msg = GroupMessage.newBuilder()
                .setTarget(member)
                .addArgstr(prefix)// pass prefix
                .addArgstr(TopJob.getname()) // pass filename
                .addArgstr(Integer.toString(TopJob.getid()))// pass job id
                .setAction(GroupMessage.Action.MAPLE_F2_WORK)
                .build();
            } 
            
            GroupMessage rcv_msg = sendMessageWaitResponse(member, send_msg);
            if (rcv_msg.getAction() == GroupMessage.Action.NODE_FREE){
                System.out.println("Member "+i+" ack.");
            } else {
                System.out.println("Member "+i+" does not ack. Should Not Happen!!!!");
            }
            
            // update job time
            TopJob = getQ().remove();
            TopJob.settime(System.currentTimeMillis());
            getQ().add(TopJob);
		}
    }
    
    public job findjob(int phase, String prefix){
        job TopJob = getQ().peek();
        while ((TopJob == null) || (getJobDone().get(TopJob.getid()) == true)){
            if (TopJob == null){
                System.out.println("Queue is empty");
                
                GroupMessage msg = null;
                // send message to master to begin phase 2
                if (phase == 1){
                    msg = GroupMessage.newBuilder()
                    .setTarget(getCurrentMember())
                    .addArgstr(prefix)
                    .setAction(GroupMessage.Action.MAPLE_PHASE_ONE_DONE)
                    .build();
                    sendMessageTo(msg,getMemberList().get(0));
                } else if (phase == 2){ 
                    msg = GroupMessage.newBuilder()
                    .setTarget(getCurrentMember())
                    .addArgstr(prefix)
                    .setAction(GroupMessage.Action.MAPLE_PHASE_TWO_DONE)
                    .build();
                    
                    for (int i = 0; i < getMemberList().size(); i++)
                        sendMessageTo(msg,getMemberList().get(i));
                } 
                
                return null;
            } else if (getJobDone().get(TopJob.getid()) == true){
                System.out.println("Job "+TopJob.getid()+" is done. Throw it out.");
                getQ().remove();
                TopJob = getQ().peek();
            }
        }
        return TopJob;
    }
    
    
    public void trimKey(LinkedList<String> joblist){
        String delims = "_";
        for( int j = 0; j < joblist.size(); j++){
            String[] tokens = joblist.get(j).split(delims);
            String str = tokens[0] +"_"+ tokens[1];
            joblist.set(j,str);
            //System.out.println(joblist.get(j));
        }
    }
    
    public void RemoveDuplicate(LinkedList<String> keylist){
        for (int i = 0; i < keylist.size(); i++)
            for (int j = i+1; j < keylist.size(); j++)
                if ((keylist.get(i)).equals(keylist.get(j))){
                    keylist.remove(j);
                    j--;    
                }
    }
    
    public void handleMapleWorkDone(Member sender, List< String > args){
        // write the done work to log !!!!!!!!!!!!!!!!!!!
        
        // mark the work in JobDone as true
        System.out.println(args.get(0)+" is done, its id is "+ args.get(1));
        if (phase == 1)
            JobDone.set(Integer.parseInt(args.get(1)),true);
        else if (phase == 2)
            Jobf2Done.set(Integer.parseInt(args.get(1)),true);
        // send new possible job
        sendNewWork(sender,args.get(2));
    }
    
    public void sendNewWork(Member member,String prefix){
        job TopJob = findjob(phase, prefix);
        if (TopJob == null)
            return;
        
        GroupMessage msg = null;
        if (phase == 1){
            
            msg = GroupMessage.newBuilder()
            .setTarget(member)
            .addArgstr(prefix)// pass prefix
            .addArgstr(TopJob.getname()) // pass filename
            .addArgstr(Integer.toString(TopJob.getid()))// pass job id
            .setAction(GroupMessage.Action.MAPLE_WORK)
            .build();
        } else if (phase == 2){
            
            msg = GroupMessage.newBuilder()
            .setTarget(member)
            .addArgstr(prefix)// pass prefix
            .addArgstr(TopJob.getname()) // pass filename
            .addArgstr(Integer.toString(TopJob.getid()))// pass job id
            .setAction(GroupMessage.Action.MAPLE_F2_WORK)
            .build();
        }
        
        sendMessageTo(msg,member);
    }
    
    public LinkedList<String> OprationSDFS(String op,String str1, String str2)
    {
        System.out.println("Operation: "+op+" "+str1+" "+str2);
        LinkedList<String> returnlist = new LinkedList<String>();
        Runtime runtime = Runtime.getRuntime();
        Process process = null;
        BufferedWriter output = null;
        try {
            LinkedList< String > cmd_array = new LinkedList< String >();
            cmd_array.add("java");
            cmd_array.add("-cp");
            cmd_array.add("../GroupMem-mp3/bin:lib/protobuf-java-2.4.1.jar");
            cmd_array.add("edu.uiuc.groupmessage.SDFSClient");
            cmd_array.add(getMemberList().get(0).getIp());
            cmd_array.add("6611");
            cmd_array.add(op);
            cmd_array.add(str1);
            if ((op.equals("put"))||(op.equals("get")))
                cmd_array.add(str2);
            process = runtime.exec(cmd_array.toArray(new String[cmd_array.size()]));
            
            
            try{
                process.waitFor();
            } catch (InterruptedException e) {
                    // stop the process and return
                    process.destroy();
                    System.out.println("Process is destroyed.------");
                    return null;
            }
            
            
            BufferedReader result = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
			while((line = result.readLine()) != null) {
				returnlist.add(line);
			}            
        } catch(IOException ex) {
            System.out.println("Unable to "+op+" "+str1+" "+str2+" in SDFS.\n");
        }
        return returnlist;
    }
    
    public GroupMessage handleMapleWork(List< String > args){
        System.out.println("I receive prefix:"+ args.get(0)+", job name "+ args.get(1)+", it's id "+ args.get(2));
        
        GroupMessage msg = GroupMessage.newBuilder()
        .setTarget(currentMember)
        .setAction(GroupMessage.Action.NODE_FREE)
        .build();
        
        mworker = new MapleWorker(this,args.get(0),args.get(1),args.get(2));
		mworker.start();
        
        return msg;
    }
    
    public void handleInitMapleRequest(List< String > args){
        // reset Maple done flag
        AllDone = false;
        
        // create a job priority queue
        queue = new PriorityQueue<job>();
        CreateJobQ(args);
        
        // Initial JobDone List
        JobDone = new ArrayList<Boolean>();
        for (int i = 0; i < (args.size()-2); i++)
            JobDone.add(false);
        System.out.println("JobDone has "+JobDone.size()+" jobs.");
        System.out.println("queue has "+queue.size()+" jobs.");
        
        mserver = new MapleMaster(this,args,phase);
		mserver.start();
    }
    
    public void CreateJobQ (List< String > args){
        //for (int i = 0; i < args.size(); i++)
        //    System.out.println("args.get("+i+") = "+args.get(i));
        
        for (int i = 2; i < args.size(); i++){
            System.out.println(args.get(i)+", id = "+(i-2));
            queue.add(new job(args.get(i),0,i-2));
        }
    }

    public void sendMapleRequestTo(LinkedList< String > arg) {
        
        GroupMessage msg = GroupMessage.newBuilder()
        .setTarget(currentMember)
        .addAllArgstr(arg)
        .setAction(GroupMessage.Action.MAPLE_REQUEST)
        .build();
        
        sendMessageTo(msg,memberList.get(0));
    }

    
	public void handleHeartbeats(Member sender) {
		LOGGER.info("Received heartbeat from node " + memberToID(sender));
		if (heartbeatFrom != null && heartbeatFrom.equals(sender)) {
			long time = System.currentTimeMillis();
			LOGGER.info("Update "
					+ memberToID(sender)
					+ "'s heartbeat timestamp to " + time/1000);
			setHeartbeatTimestamp(time);
		}
	}

	public void handleResetMemberList(List< Member > member_list) {
		LOGGER.warning("Reset the member list");
		setMemberList(member_list);
		startHeartbeatClient();
		startFailureDetector();
		LOGGER.info(member_list.toString());
	}

	public GroupMessage handleJoinRequest(Member joiner) {
		// Log the event
		LOGGER.warning("The member " + memberToID(joiner) + " just joins.");

		// Update the memberList
		addMember(joiner);

		// Start hearbearting and failure detection
		startHeartbeatClient();
		startFailureDetector();

		// Broadcast the join message
		broadcastTargetJoin(joiner);

		// Send back the latest memberList to joiner
		return GroupMessage.newBuilder()
			.setTarget(joiner)
			.setAction(GroupMessage.Action.RESET_MEMBERLIST)
			.addAllMember(memberList)
			.build();
	}

	public void handleTargetJoins(Member joiner) {
		// Log the event
		LOGGER.warning("The member " + memberToID(joiner) + " just joins.");

		// Update the memberList
		if (!joiner.equals(currentMember)) {
			addMember(joiner);
		} else {
			LOGGER.severe("Duplicated Member Joins");
		}

		// Start hearbearting and failure detection
		startHeartbeatClient();
		startFailureDetector();
	}

	public void handleTargetLeaves(Member leaver) {
		// Log the event
		LOGGER.warning("The member " + memberToID(leaver) + " just leaves.");

		// Update the memberList
		if (!removeMember(leaver)) {
			LOGGER.severe("Somebody leaves without being on the list");
		}

		// Restart heartbeating
		startHeartbeatClient();
		startFailureDetector();
	}

	public void handleTargetFails(Member loser) {
		// Log the event
		LOGGER.warning("The member " + memberToID(loser) + " just fails.");

		// Update the memberList
		if (!removeMember(loser)) {
			LOGGER.severe("Somebody fails but he/she is not on the list");
		}

		// Restart heartbeating and failure detection
		startHeartbeatClient();
		startFailureDetector();
	}

	public void broadcastTargetJoin(Member joiner) {
		GroupMessage msg = GroupMessage.newBuilder()
			.setTarget(joiner)
			.setAction(GroupMessage.Action.TARGET_JOINS)
			.build();
		LinkedList< Member > list = new LinkedList< Member >(memberList);
		list.remove(joiner);
		list.remove(currentMember);
		broadcastMessage(msg, list);
	}

	public void broadcastTargetFail(Member loser) {
		GroupMessage msg = GroupMessage.newBuilder()
			.setTarget(loser)
			.setAction(GroupMessage.Action.TARGET_FAILS)
			.build();
		LinkedList< Member > list = new LinkedList< Member >(memberList);
		list.remove(loser);
		list.remove(currentMember);
		broadcastMessage(msg, list);
	}

	public void sendJoinRequestTo() {
		try {
			// Open I/O
			Socket sock = new Socket(portalMember.getIp(), portalMember.getPort());
			InputStream sock_in = sock.getInputStream();
			OutputStream sock_out = sock.getOutputStream();

			// Send Join Request
			GroupMessage.newBuilder()
				.setTarget(currentMember)
				.setAction(GroupMessage.Action.JOIN_REQUEST)
				.build()
				.writeDelimitedTo(sock_out);
			sock_out.flush();

			// Update the membership list
			GroupMessage msg = GroupMessage.parseDelimitedFrom(sock_in);
			processMessage(msg);

			// Close I/O
			sock_out.close();
			sock_in.close();
			sock.close();
		} catch(Exception ex) {
			System.out.println(ex.getMessage());
		}
	}
    
    public GroupMessage sendMessageWaitResponse(Member target, GroupMessage msg) {
        MemListNodeWorker worker = new MemListNodeWorker(target, msg);
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException ex) {
            System.out.println(ex.getMessage());
        }
        return worker.getRcvMsg();
    }
    class MemListNodeWorker extends Thread {
        private Member target;
        private GroupMessage send_msg;
        private GroupMessage rcv_msg;
        MemListNodeWorker(Member target, GroupMessage send_msg) {
            this.target = target;
            this.send_msg = send_msg;
        }
        
        public GroupMessage getRcvMsg() {
            return rcv_msg;
        }
        
        public void run() {
            try {
                Socket sock = new Socket(target.getIp(), target.getPort());
                InputStream sock_in = sock.getInputStream();
                OutputStream sock_out = sock.getOutputStream();
                send_msg.writeDelimitedTo(sock_out);
                sock_out.flush();
                rcv_msg = GroupMessage.parseDelimitedFrom(sock_in);
                sock_out.close();
                sock_in.close();
                sock.close();
            } catch (UnknownHostException ex) {
                System.out.println(ex.getMessage());
            } catch (IOException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

	public void broadcastMessage(GroupMessage msg, List< Member > list) {
		for (Member member : list) {
			sendMessageTo(msg, member);
		}
	}

	public void sendMessageTo(GroupMessage msg, Member receiver) {
		new Thread() {
			private GroupMessage msg;
			private Member receiver;

			public Thread initialize(GroupMessage msg, Member receiver) {
				this.msg = msg;
				this.receiver = receiver;
				return this;
			}
			public void run() {
				LOGGER.info(
						"Send request to " + memberToID(receiver) +
						" with Target " + memberToID(msg.getTarget()) +
						" and Action " + msg.getAction().name()
						);
				try {
					Socket sock = new Socket(receiver.getIp(), receiver.getPort());
					OutputStream sock_out = sock.getOutputStream();
					msg.writeDelimitedTo(sock_out);
					sock_out.flush();
					sock_out.close();
					sock.close();
				} catch(Exception ex) {
					System.out.println(ex.getMessage());
				}
			}
		}.initialize(msg, receiver).start();
	}

	public void broadcastTargetLeave() {
		GroupMessage msg = GroupMessage.newBuilder()
			.setTarget(currentMember)
			.setAction(GroupMessage.Action.TARGET_LEAVES)
			.build();
		LinkedList< Member > list = new LinkedList< Member >(memberList);
		list.remove(currentMember);
		broadcastMessage(msg, list);
		stopHeartbeatClient();
		stopFailureDetector();
	}

	public void setMemberList(List< Member > member_list) {
		synchronized(memberList) {
			this.memberList = new LinkedList< Member >(member_list);
		}
		sortMemberList();
	}

	public void addMember(Member member) {
		synchronized(memberList) {
			this.memberList.add(member);
			System.out.println(memberList.toString());
		}
		sortMemberList();
	}

	public void sortMemberList() {
		synchronized(memberList) {
			// Sort the memberList by timestamp to ensure everyone has the same order
			Collections.sort(memberList, new Comparator< Member >() {
					@Override
					public int compare(Member m1, Member m2) {
					return
					Integer.valueOf(m1.getTimestamp()).compareTo(m2.getTimestamp());
					}
					});

			// Update the member HeartbeatFrom and HeartbeatTo
			int index = memberList.indexOf(currentMember);
			int size = memberList.size();
			if (size > 0) {
				heartbeatFrom = memberList.get((index + size - 1) % size);
				heartbeatTo = memberList.get((index + 1) % size);
			} else {
				heartbeatFrom = null;
				heartbeatTo = null;
			}
		}
	}

	public boolean removeMember(Member member) {
		boolean result;
		synchronized(memberList) {
			result = this.memberList.remove(member);
			LOGGER.info(memberList.toString());
		}
		sortMemberList();
		return result;
	}

	public static String getCurrentIp() {
		try {
			NetworkInterface nif = NetworkInterface.getByName("en1");
			Enumeration<InetAddress> addrs = nif.getInetAddresses();
			while (addrs.hasMoreElements()) {
				InetAddress addr = addrs.nextElement();
				if (addr instanceof Inet4Address) {
					return addr.getHostAddress();
				}
			}
			return null;
		} catch(SocketException ex) {
			System.out.println(ex.getMessage());
			System.exit(-1);
			return null;
		}
	}

	public static String memberToID(Member member) {
		StringBuilder str = new StringBuilder();
		return str.append(member.getIp()).append("_")
			.append(member.getPort()).append("_")
			.append(member.getTimestamp())
			.toString();
	}

	public String toString() {
		return currentMember.toString() + "\n" + portalMember.toString();
	}

	public void startHeartbeatServer() {
		new HeartbeatServer(this).start();
	}

	public void startHeartbeatClient() {
		// Stop the previous heartbeat if existing
		if (heartbeatClientTimer != null) {
			stopHeartbeatClient();
		}

		// Setup the heartbeatClientTimer
		LOGGER.info("Start heartbeating to " + memberToID(heartbeatTo));
		heartbeatClientTimer = new Timer("HeartbeatClient");
		heartbeatClientTimer.scheduleAtFixedRate(
				new HeartbeatClient(this, heartbeatTo), 0, 1000);
	}

	public void stopHeartbeatClient() {
		if (heartbeatClientTimer != null) {
			heartbeatClientTimer.cancel();
			heartbeatClientTimer = null;
		}
	}

	public void startFailureDetector() {
		// Stop the previous detector if existing
		if (detectorTimer != null) {
			stopFailureDetector();
		}

		// Initialize timestamp
		setHeartbeatTimestamp(System.currentTimeMillis());

		// Setup the detectorTimer
		detectorTimer = new Timer("FailureDetector");
		detectorTimer.scheduleAtFixedRate(
				new FailureDetector(this), 0, 100);
	}

	public void stopFailureDetector() {
		if (detectorTimer != null) {
			detectorTimer.cancel();
			detectorTimer = null;
			setHeartbeatTimestamp(-1);
		}
	}

	public void detectFailure() {
		if (heartbeatFrom == null) {
			return;
		}
		long current_time = System.currentTimeMillis();
		if (current_time - getHeartbeatTimestamp() > 4500) {
			LOGGER.warning("Detect failure.");
			stopFailureDetector();
			broadcastTargetFail(heartbeatFrom);
			handleTargetFails(heartbeatFrom);
		}
	}

	public static void main(String[] args) {

		// Check for insufficient arguements
		if (args.length < 3) {
			System.out.println("Usage:");
			System.out.println(
					"java edu.uiuc.groupmessage.MemListNode " +
					"<listen_port> <portal_ip> <portal_port>"
					);
			System.exit(-1);
		}

		// Process the arguments
		Member current_member = Member.newBuilder()
			.setPort(Integer.parseInt(args[0]))
			.setIp(getCurrentIp())
			.setTimestamp((int)(System.currentTimeMillis()/1000))
			.build();
		Member portal_member = Member.newBuilder()
			.setPort(Integer.parseInt(args[2]))
			.setIp(args[1])
			.build();
		MemListNode node = new MemListNode(current_member, portal_member);

		// Run server that accepts join/leave/fail request
		node.runServer();

		// Join the Group
		node.sendJoinRequestTo();

		// Handle commands
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		String str;
		System.out.println("Please enter command:");
        
		try {
			while ((str = input.readLine()) != null) {
                
                Scanner sc = new Scanner(str);
                String s = sc.next();
                
				if (str.equals("leave")) {
					node.broadcastTargetLeave();
				} else if (str.equals("join")) {
					node.sendJoinRequestTo();
				} else if (s.equals("maple")){                    
                    LinkedList< String > arg = new LinkedList< String >();
                    while (sc.hasNext())
                        arg.add(sc.next());                        
                    node.sendMapleRequestTo(arg);
                }
				System.out.println("Please enter command:");
			}
		} catch(IOException ex) {
			System.out.println(ex.getMessage());
			System.exit(-1);
		}
	}
}
