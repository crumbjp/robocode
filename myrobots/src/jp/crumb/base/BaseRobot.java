/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.crumb.base;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jp.crumb.utils.Enemy;
import jp.crumb.utils.Logger;
import jp.crumb.utils.MovingPoint;
import jp.crumb.utils.Pair;
import jp.crumb.utils.Point;
import jp.crumb.utils.RobotPoint;
import jp.crumb.utils.Util;
import robocode.BattleEndedEvent;
import robocode.Bullet;
import robocode.BulletHitBulletEvent;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.Condition;
import robocode.CustomEvent;
import robocode.DeathEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.MessageEvent;
import robocode.RobotDeathEvent;
import robocode.RoundEndedEvent;
import robocode.ScannedRobotEvent;
import robocode.StatusEvent;
import robocode.TeamRobot;
import robocode.WinEvent;


/**
 *
 * @author crumb
 */
abstract public class BaseRobot<T extends BaseContext> extends TeamRobot {
    protected static final boolean isPaint = true;
//    protected Logger logger = new Logger(0);
    protected Logger logger = new Logger(
            Logger.LOGLV_PROSPECT1 | Logger.LOGLV_FIRE1
            );


    protected static final double MOVE_COMPLETE_THRESHOLD = 1.0;

    protected static final int SCAN_STALE = 9;
    protected static final int SYSTEM_BUG_TICKS = 30;
      
    protected T ctx = createContext(null);
    
   
    protected static List<String> teammate = new ArrayList<>(5);
    protected static boolean isLeader = false;
    protected static String leader = null;
    protected static String name;

    protected static int allEnemies = 0;
    // Current informations
    protected Map<String, Enemy> enemyMap = new HashMap<>(15,0.95f);
    protected Map<String,BulletInfo> bulletList = new HashMap<>(50,0.95f);
    

    private Condition eachTickTimer = new Condition("eachTickTimer",10) {
        @Override
        public boolean test() {
            return true;
        }
    };
    private Condition firstTickTimer = new Condition("firstTickTimer",90) {
        @Override
        public boolean test() {
            if ( getTime() == 1 ) {
                return true;
            }
            if ( getTime() - lastScanTick  > 20 ) {
                lastScanTick = ctx.my.time - 10;
                logger.trace("SKIP BUG !! %d", ctx.my.time);
                return true;
            }
            return false;
//                return true;
//            }
//            return false;
        }
    };
    private void initEventPriority(){

	this.setEventPriority("ScannedRobotEvent",10);
	this.setEventPriority("HitRobotEvent",10);
	this.setEventPriority("HitWallEvent",10);
	this.setEventPriority("HitByBulletEvent",10);
	this.setEventPriority("BulletHitEvent",10);
	this.setEventPriority("BulletHitBulletEvent",10);
	this.setEventPriority("BulletMissedEvent",10);
	this.setEventPriority("RobotDeathEvent",10);
	this.setEventPriority("CustomEvent",10);
//	this.setEventPriority("SkippedTurnEvent",10);
//	this.setEventPriority("WinEvent",10);
//	this.setEventPriority("DeathEvent",10);
     }

    protected void updateCurrent() {
        Util.NOW = getTime();
        if ( Util.NOW == ctx.my.time ) {
            return;
        }
        ctx.prevRadarHeadingRadians = ctx.curRadarHeadingRadians;   
        ctx.curGunHeadingRadians = getGunHeadingRadians();
        ctx.curRadarHeadingRadians = getRadarHeadingRadians();
        
        ctx.gunHeat = getGunHeat();
        ctx.others = getOthers();
        ctx.enemies = ctx.others; // will be decl by each TeammateInfoEvent

        ctx.curTurnRemainingRadians = getTurnRemainingRadians();
        ctx.curGunTurnRemainingRadians = getGunTurnRemainingRadians();
        ctx.curRadarTurnRemainingRadians = getRadarTurnRemainingRadians();
        ctx.curDistanceRemaining = getDistanceRemaining();

        RobotPoint prevMy = ctx.my;
        ctx.my = new RobotPoint();
        ctx.my.time = Util.NOW;
        ctx.my.timeStamp = Util.NOW;
        ctx.my.energy = getEnergy();
        ctx.my.velocity = getVelocity();
        ctx.my.x = getX();
        ctx.my.y = getY();
        ctx.my.headingRadians = getHeadingRadians();
        ctx.my.setPrev(prevMy);
    }

 
    

    private void impactBullet(String key){
        broadcastMessage(new BulletImpactEvent(key));
        removeBulletInfo(key);
    }
    protected void addBulletInfo(BulletInfo bulletInfo) {
        logger.fire2("SHOT: %s",bulletInfo.bulletName);
        bulletList.put(bulletInfo.bulletName,bulletInfo);
    }
    protected void removeBulletInfo(String key){
        logger.fire2("IMPACT: %s",key);
        bulletList.remove(key);
    }
    
    protected Map.Entry<String,BulletInfo> cbBulletMissed(BulletMissedEvent e) {
        Bullet bullet = e.getBullet();
        Point dst = new Point(bullet.getX(),bullet.getY());
        Map.Entry<String,BulletInfo> entry = Util.calcBulletSrc(ctx.my.time,bullet,bulletList);
        if ( entry == null ) {
            logger.fire1("Unknown bullet missed: ");
        }else{
            impactBullet(entry.getKey());
       }
        logger.fire3("MISS: %s",dst);
        return entry;
    }
    
    protected Map.Entry<String, BulletInfo>  cbBulletHit(BulletHitEvent e){
        Bullet bullet = e.getBullet();
        Point dst = new Point(bullet.getX(),bullet.getY());
        String victim = bullet.getVictim();

        double range = 0.0;
        double aimDistance = 0.0;
        BulletInfo info = null;
        Map.Entry<String,BulletInfo> entry = Util.calcBulletSrc(ctx.my.time,bullet,bulletList);
        if ( entry == null ) {
            logger.fire1("HIT (by chance): %s: %2.2f(%2.2f)  %s => %s",victim,aimDistance,range,"NULL",dst);
        }else{
            impactBullet(entry.getKey());
            info = entry.getValue();
            aimDistance = entry.getValue().distance;
            range = info.src.calcDistance(dst);
            if ( info.targetName.equals(victim) && Math.abs(aimDistance - range) < Util.tankSize) {
                logger.fire1("HIT: %s : %2.2f(%2.2f)  %s => %s",victim,aimDistance,range,info.src,dst);
            }else {
                logger.fire1("HIT (by chance): %s(%s): %2.2f(%2.2f)  %s => %s",victim,info.targetName,aimDistance,range,info.src,dst);
            }
        }
        return entry;
    }


    protected Map.Entry<String,BulletInfo> cbBulletHitBullet(BulletHitBulletEvent e){
        Bullet bullet = e.getBullet();
        Point dst = new Point(bullet.getX(),bullet.getY());
        Map.Entry<String,BulletInfo> entry = Util.calcBulletSrc(ctx.my.time,bullet,bulletList);
        if ( entry == null ) {
            logger.fire1("Unknown bullet hit: ");
        }else{
            impactBullet(entry.getKey());
        }
        logger.fire1("INTERCEPT: %s",dst);
        return entry;
    }
    

    protected void cbHitByBullet(HitByBulletEvent e) {}

    private void sendMyInfo(){
        RobotPoint next2My  = new RobotPoint(ctx.my);
        prospectNextMy(next2My,2);

        Enemy my = new Enemy();
        my.time = ctx.my.time +2;
        my.timeStamp = my.time;
        my.name = name;
        my.x = next2My.x;
        my.y = next2My.y;
        my.headingRadians = next2My.headingRadians;
        my.velocity = next2My.velocity;
        my.energy = ctx.my.energy;
        broadcastMessage(new TeammateInfoEvent(my,isLeader));
    }
    private void dispatchMessage(MessageEvent e) {
        Serializable event = e.getMessage();
        if (event instanceof ScanEnemyEvent ) {
            ScanEnemyEvent ev = (ScanEnemyEvent)event;
            cbScannedRobot(ev.e);
        }else if (event instanceof TeammateInfoEvent ) {
            ctx.enemies--;
            TeammateInfoEvent ev = (TeammateInfoEvent)event;
            if ( ev.isLeader ) {
                leader = ev.e.name;
            }
            ctx.nextMateMap.put(ev.e.name, ev.e);
        }else if (event instanceof BulletEvent ) {
            BulletEvent ev = (BulletEvent)event;
            addBulletInfo(ev.bulletInfo);
        }else if (event instanceof BulletImpactEvent ) {
            BulletImpactEvent ev = (BulletImpactEvent)event;
            removeBulletInfo(ev.key);
        }else{
            cbExtMessage(e);
        }
    }

    protected void goPoint(){
        Pair<Double,Double> go = calcGoPoint();
        if ( go == null ) {
            return;
        }
        doAhead(go.first);
        doTurnRightRadians(go.second);
    }

    private void preScannedRobot(ScannedRobotEvent e) {
        // Message will reach to teammate at next turn !!
        if ( ! isTeammate(e.getName())) {
            Enemy enemy = createEnemy(e);
            cbScannedRobot(enemy);
            Enemy next = new Enemy(enemy);
            prospectNextEnemy(next);
            this.broadcastMessage(new ScanEnemyEvent(next));
        }
    }
    long lastScanTick = 20;
    protected Enemy cbScannedRobot(Enemy enemy) {
        lastScanTick = enemy.time;
        logger.scan("%15s : %s : %d",enemy.name,enemy,enemy.time);
        Enemy prevEnemy = enemyMap.get(enemy.name);
        if ( prevEnemy == null )  { // The first time
            if ( enemy.energy > 120 ) {
                enemy.role = Enemy.ROLE_LEADER;
            }else if ( enemy.energy > 100 ) {
                enemy.role = Enemy.ROLE_DROID;
            }else {
                enemy.role = Enemy.ROLE_ROBOT;
            }
        }
        if ( prevEnemy != null ) {
            if ( prevEnemy.time == enemy.time ) {
                return null;
            }else if ((enemy.time-prevEnemy.timeStamp) < SCAN_STALE ) {
                enemy.role = prevEnemy.role;
                enemy.setPrev(prevEnemy);
            }
        }
        enemyMap.put(enemy.name, enemy);
        return enemy;
    }
    protected void cbRobotDeath(RobotDeathEvent e) {
        Enemy enemy = enemyMap.get(e.getName());
        if ( enemy != null ) {
            enemy.timeStamp = 0;
            return;
        }
        ctx.nextMateMap.remove(e.getName());
    }    
        
    protected final void setDestination(Point dst){
        logger.move_log("DST: %s", dst);
        this.ctx.destination = dst;
    }
    protected void cbProspectNextTurn(){ }
    protected void cbUnprospectiveNextTurn(){}
    protected final Pair<Double,Double> calcGoPoint(){
        if ( ctx.destination == null ) {
            return null;
        }
        if ( ctx.destination.calcDistance(ctx.my) < 1 ) {
            return new Pair<>(0.0,0.0);
        }
        double bearingRadians = ctx.my.calcRadians(ctx.destination);
        double distance = ctx.my.calcDistance(ctx.destination);

        double runTime = Util.calcRoughRunTime(distance,ctx.my.velocity);

        Pair<Double,Integer> turn = ctx.calcAbsTurnRadians(bearingRadians);
        double turnRadians = turn.first;
        distance *= turn.second;
        
        double turnTime = Math.abs(turnRadians/Util.turnSpeedRadians(ctx.my.velocity));
        if ( runTime <= turnTime ) {
            distance = 0;
        }
        if ( Math.abs(distance) < MOVE_COMPLETE_THRESHOLD ) { 
            distance = 0.0;
        }
        return new Pair<>(distance,turnRadians);
    }
    
    protected final BaseContext defalutCreateContext(BaseContext in) {
        if ( in == null ) {
            return new BaseContext();
        }else{
            return new BaseContext(in);
        }
    }
    protected final void prospectNextMy(RobotPoint nextMy,long delta) {
        T curCtx = null;
        for (int i = 0; i < delta; i++) {
            curCtx = prospectNextMy(nextMy, curCtx);
        }
    }

    protected final T prospectNextMy(RobotPoint nextMy,T curContext) {
        if ( ctx.my.time == 1 ) {
            return null;
        }
        T backupContext = ctx;
        if ( curContext == null ) {
            curContext =  createContext(ctx);
        }
        ctx = curContext;
        this.cbProspectNextTurn();
        this.cbMoving();

        Pair<Double,Double> go = this.calcGoPoint();
        ctx.my = new RobotPoint(nextMy);
        if ( go != null ) {
//        this.cbThinking();
//        
            MovingPoint delta = new MovingPoint();
            delta.time = 1;
            delta.headingRadians = go.second;
            delta.velocity = go.first;
            nextMy.setDelta(delta);
            nextMy.prospectNext();
        }else {
            nextMy.time++;
        }
        ctx = backupContext;
//        nextMy.prospectNext();
//        if ( true ) {
//            return null;
//        }
        return curContext;
    }

//    protected void doFire(double power, double distance,String targetName ) {
//        doFire(power, distance, targetName,0);
//    }
    protected void doFire(double power, double distance,RobotPoint target, int type) {
        if ( ctx.gunHeat != 0 ) {
            return;
        }
        if( target != null ) {
            logger.fire1("FIRE(x%02x): ( %2.2f ) => %2.2f d : %2.2f",type,power,Math.toDegrees(ctx.curGunHeadingRadians),distance);
            double bulletVelocity = Util.bultSpeed(power);
            MovingPoint src = new MovingPoint(
                    ctx.my.x , ctx.my.y , ctx.my.time,
                    ctx.curGunHeadingRadians,
                    bulletVelocity
                    );
            BulletInfo bulletInfo = new BulletInfo(name,target.name,distance,src,type,power);
            addBulletInfo(bulletInfo);
            broadcastMessage(new BulletEvent(bulletInfo));
        }
        this.paint(getGraphics());
        super.fire(power); // No return
    }


    
    public boolean isStale(Enemy e) {
         if ( e == null || (ctx.my.time - e.timeStamp) > SCAN_STALE ) {
             return true;
         }
         return false;
    }

    
    abstract protected T createContext(T in);
    protected void cbThinking() {}
    protected void cbMoving() {}
//    protected void cbUnprospectiveMoving() {}
    protected void cbGun() {}
    protected void cbRadar() {}
    protected void cbFiring() {}
    protected void cbFirst() {}

    protected boolean prospectNextEnemy(Enemy enemy) {
        return enemy.prospectNext();
    }

    protected void cbStatus(StatusEvent e){}

    protected void cbHitWall(HitWallEvent e) {
        logger.crash("CLASH WALL: %s : %f",ctx.my,e.getBearing());
    }
    protected void cbHitRobot(HitRobotEvent e) {
        logger.crash("CLASH (%s): %s : %f",e.getName(),ctx.my,e.getBearing());
    }
    protected void cbExtMessage(MessageEvent e) {}

    protected Enemy createEnemy(ScannedRobotEvent e) {
        return new Enemy(ctx.my, e);
    }    


  
    @Override
    public void run() {
        Util.init(
                getBattleFieldWidth(),
                getBattleFieldHeight(),
                getWidth(),
                getHeight(),
                getGunCoolingRate()
                );
        initEventPriority();
        name = getName();
        String [] array =this.getTeammates();
        if ( array != null ) {
            teammate = Arrays.asList(array);
            if ( getEnergy() == 200 ) {
                isLeader = true;
                leader = name;
            }
        }
        if ( allEnemies <= 0 ) {
            allEnemies = getOthers() - teammate.size();
        }

        addCustomEvent(this.firstTickTimer);
        addCustomEvent(this.eachTickTimer);
//        execute();
    }
  @Override
    public boolean isTeammate(String name) {
        return teammate.contains(name);
    }

    @Override
    public void broadcastMessage(Serializable e ){
        try {
            super.broadcastMessage(e);
        } catch (IOException ex) {
            Logger.log("Send message error %s", ex.getMessage() );
        }
    }
    @Override
    public void sendMessage(String name,Serializable e ){
        try {
            super.sendMessage(name,e);
        } catch (IOException ex) {
            Logger.log("Send message error %s", ex.getMessage() );
        }
    }
    

    
    
    protected void doAhead(double distance) {
        super.setAhead(distance);
        ctx.curDistanceRemaining = distance;
    }
    protected void doTurnRightRadians(double radians) {
        super.setTurnRightRadians(radians);
        ctx.curTurnRemainingRadians = radians;
    }
    protected void doTurnGunRightRadians(double radians) {
        logger.gun3("TURN: %2.2f : %2.2f => %2.2f",Math.toDegrees(ctx.curGunHeadingRadians),Math.toDegrees(ctx.curGunTurnRemainingRadians),Math.toDegrees(radians));
        super.setTurnGunRightRadians(radians);
        ctx.curGunTurnRemainingRadians = radians;
    }

    protected void doTurnRadarRightRadians(double radians) {
        logger.radar3("TURN: %2.2f : %2.2f => %2.2f",Math.toDegrees(ctx.curRadarHeadingRadians),Math.toDegrees(ctx.curRadarTurnRemainingRadians),Math.toDegrees(radians));
        super.setTurnRadarRightRadians(radians);
        ctx.curRadarTurnRemainingRadians = radians;
    }

    @Override
    public void onDeath(DeathEvent event) {
        dumpLog();
    }
    static int WIN = 0;
    @Override
    public void onWin(WinEvent event) {
        WIN++;
        dumpLog();
    }
    
    @Override
    public void onRoundEnded(RoundEndedEvent event) {
        dumpLog();
    }
    
    @Override
    public void onBattleEnded(BattleEndedEvent event) {
        dumpLog();
    }

    static long past = 0;
    private static void timeLog(String message) {
//        long t = System.nanoTime();
//        if ( t-past > 50000000 ) {
//            Logger.log("%s : %2.2f",message,(double)(t-past)/1000000.0);
//        }
//        past = t;
    }

    boolean first = true;
    @Override
    public void onCustomEvent(CustomEvent event) {
        long startTime = System.nanoTime();
        this.setInterruptible(true);
        if (event.getCondition().equals(this.firstTickTimer) ) {
            //this.removeCustomEvent(firstTickTimer);
            if ( first ) {
                first = false;
                cbFirst();
            }
            execute();
            return;
        }
        if (event.getCondition().equals(this.eachTickTimer) ) {
            updateCurrent();
            sendMyInfo();
            
            timeLog("FIRST");
            for ( ScannedRobotEvent e: this.getScannedRobotEvents() ) {
                this.preScannedRobot(e);
            }
            timeLog("SCAN");
            for ( MessageEvent e : this.getMessageEvents() ) {
                this.dispatchMessage(e);
            }
            timeLog("MESSAGE");
            for ( BulletHitBulletEvent e: this.getBulletHitBulletEvents() ) {
                this.cbBulletHitBullet(e);
            }
            timeLog("HIT BULLET");
            for ( BulletHitEvent e: this.getBulletHitEvents() ) {
                this.cbBulletHit(e);
            }
            timeLog("HIT");
            for ( BulletMissedEvent e: this.getBulletMissedEvents() ) {
                this.cbBulletMissed(e);
            }
            timeLog("MISS");
            for ( HitByBulletEvent e: this.getHitByBulletEvents() ) {
                this.cbHitByBullet(e);
            }
            timeLog("BY BULLET");

            for ( RobotDeathEvent e: this.getRobotDeathEvents() ) {
                this.cbRobotDeath(e);
            }
            for ( HitRobotEvent e: this.getHitRobotEvents() ) {
                this.cbHitRobot(e);
            }
            for ( HitWallEvent e: this.getHitWallEvents() ) {
                this.cbHitWall(e);
            }
            for ( StatusEvent e: this.getStatusEvents() ) {
                this.cbStatus(e);
            }
            timeLog("EVENTS");
            this.cbProspectNextTurn();
            timeLog("PNEXT");
            this.cbUnprospectiveNextTurn();
            timeLog("UNEXT");
            this.cbThinking();
            timeLog("THINK");
            this.cbMoving();
            timeLog("MOVE");
            this.goPoint();
            timeLog("GO");
            // this.cbUnprospectiveMoving();
            this.cbGun();
            timeLog("GUN");
            this.cbRadar();
            timeLog("RADAR");
            this.cbFiring();
            timeLog("FIRE");
        }
        this.paint(getGraphics());
        long endTime = System.nanoTime();
        if ( endTime-startTime > 10000000 ) {
            logger.log("E : %2.2f",(double)(endTime-startTime)/1000000.0);
        }
        execute();
    }
    
    protected static void drawRound(Graphics2D g, double x, double y, double r) {
        if (isPaint) {
            g.drawRoundRect((int) (x - r / 2), (int) (y - r / 2), (int) r, (int) r, (int) r + 2, (int) r + 2);
        }
    }

    protected static final float PAINT_OPACITY=0.5f;
    protected void paint(Graphics2D g) {
        if (isPaint) {
//            g.setColor(new Color(0, 0.7f, 0, PAINT_OPACITY));
//            g.setStroke(new BasicStroke(1.0f));
//            drawRound(g, ctx.my.x, ctx.my.y, 400 * 2);
//            drawRound(g, ctx.my.x, ctx.my.y, 600 * 2);
//            float[] dash = new float[2];
//            dash[0] = 0.1f;
//            dash[1] = 0.1f;
//            g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 1.0f, dash, 0.0f));
//            drawRound(g, ctx.my.x, ctx.my.y, 100 * 2);
//            drawRound(g, ctx.my.x, ctx.my.y, 300 * 2);
//            drawRound(g, ctx.my.x, ctx.my.y, 500 * 2);

            g.setStroke(new BasicStroke(1.0f));
            g.setColor(new Color(0, 0.7f, 0, PAINT_OPACITY));

            g.drawLine((int) ctx.my.x, (int) ctx.my.y, (int) (Math.sin(ctx.curGunHeadingRadians) * Util.fieldFullDistance + ctx.my.x), (int) (Math.cos(ctx.curGunHeadingRadians) * Util.fieldFullDistance + ctx.my.y));

            double deltaRadians = Util.calcTurnRadians(ctx.curRadarHeadingRadians, ctx.prevRadarHeadingRadians) / 10;
            if (deltaRadians != 0.0) {
                int[] xs = new int[3];
                int[] ys = new int[3];
                xs[0] = (int) ctx.my.x;
                ys[0] = (int) ctx.my.y;
                double radians = ctx.curRadarHeadingRadians;
                for (int i = 1; i < 10; i++) {
                    xs[1] = (int) (Math.sin(radians) * Util.fieldFullDistance + ctx.my.x);
                    ys[1] = (int) (Math.cos(radians) * Util.fieldFullDistance + ctx.my.y);
                    radians += deltaRadians;
                    xs[2] = (int) (Math.sin(radians) * Util.fieldFullDistance + ctx.my.x);
                    ys[2] = (int) (Math.cos(radians) * Util.fieldFullDistance + ctx.my.y);
                    g.setColor(new Color(i * 0.03f, i * 0.03f, 1.0f, 0.1f));
                    Polygon triangle = new Polygon(xs, ys, 3);
                    g.fill(triangle);
                }
            } else {
                g.setColor(new Color(0.03f, 0.03f, 1.0f, 0.1f));
                g.drawLine((int) ctx.my.x, (int) ctx.my.y, (int) (Math.sin(ctx.curRadarHeadingRadians) * Util.fieldFullDistance + ctx.my.x), (int) (Math.cos(ctx.curRadarHeadingRadians) * Util.fieldFullDistance + ctx.my.y));
            }

            g.setStroke(new BasicStroke(1.0f));
            g.setColor(new Color(0, 1.0f, 0, PAINT_OPACITY));
//            g.drawString(String.format("( %2.2f , %2.2f )", ctx.my.x, ctx.my.y), (int) ctx.my.x - 20, (int) ctx.my.y - 55);
//            g.drawString(String.format("heat: %2.2f", getGunHeat()), (int) ctx.my.x - 20, (int) ctx.my.y - 65);
//            g.drawString(String.format("velo: %2.1f", getVelocity()), (int) ctx.my.x - 20, (int) ctx.my.y - 75);
            RobotPoint mypoint = new RobotPoint(ctx.my);
            T curCtx = null;
            for (int i = 1; i <= 20; i++) {
                drawRound(g, mypoint.x, mypoint.y, 2);
                curCtx = prospectNextMy(mypoint, curCtx);
            }
            g.setStroke(new BasicStroke(4.0f));
            g.setColor(new Color(0, 1.0f, 0, PAINT_OPACITY));
            if (ctx.destination != null) {
                drawRound(g, ctx.destination.x, ctx.destination.y, 10);
            }

            g.setStroke(new BasicStroke(1.0f));
            for (Map.Entry<String, Enemy> e : ctx.nextMateMap.entrySet()) {
                Enemy r = e.getValue();
                g.setColor(new Color(0, 1.0f, 0, PAINT_OPACITY));
                drawRound(g, r.x, r.y, 35);
//                g.drawString(String.format("%s : %s", r.name, r), (int) r.x - 20, (int) r.y - 30);
            }
            for (Map.Entry<String, Enemy> e : enemyMap.entrySet()) {
                Enemy r = e.getValue();
                g.setColor(new Color(0, 1.0f, 1.0f, PAINT_OPACITY));
                drawRound(g, r.x, r.y, 35);
//                g.drawString(String.format("%s : %s", r.name, r), (int) r.x - 20, (int) r.y - 30);
            }
            for (Map.Entry<String, BulletInfo> e : bulletList.entrySet()) {
                BulletInfo info = e.getValue();
                g.setStroke(new BasicStroke(1.0f));
                g.setColor(new Color(0.3f, 0.5f, 1.0f, PAINT_OPACITY));
                g.drawLine((int) info.src.x, (int) info.src.y, (int) (Math.sin(info.src.headingRadians) * Util.fieldFullDistance + info.src.x), (int) (Math.cos(info.src.headingRadians) * Util.fieldFullDistance + info.src.y));
                g.setStroke(new BasicStroke(4.0f));
                Point dst = Util.calcPoint(info.src.headingRadians, info.distance).add(info.src);
                drawRound(g, dst.x, dst.y, 5);
            }


        }
    }

    protected void dumpLog(){
        logger.log("*** %d/%d ***",WIN,getRoundNum()+1);
    }

    
    
    @Deprecated
    @Override
    public void setAhead(double distance) {
        throw new UnsupportedOperationException("Not permitted");
    }    
    @Deprecated
    @Override
    public void setBack(double distance) {
        throw new UnsupportedOperationException("Not permitted");
    }
    @Deprecated
    @Override
    public void setTurnRight(double degrees) {
        throw new UnsupportedOperationException("Not permitted");
    }
    @Deprecated
    @Override
    public void setTurnLeft(double degrees) {
        throw new UnsupportedOperationException("Not permitted");
    }
    @Deprecated
    @Override
    public void setTurnGunRight(double degrees) {
        throw new UnsupportedOperationException("Not permitted");
    }
    @Deprecated
    @Override
    public void setTurnGunLeft(double degrees) {
        throw new UnsupportedOperationException("Not permitted");
    }
    @Deprecated
    @Override
    public void setTurnRadarRight(double degrees) {
        throw new UnsupportedOperationException("Not permitted");
    }
    @Deprecated
    @Override
    public void setTurnRadarLeft(double degrees) {
        throw new UnsupportedOperationException("Not permitted");
    }
    @Deprecated
    @Override
    public void fire(double power) {
        throw new UnsupportedOperationException("Not permitted");
    }
    @Deprecated
    @Override
    public Bullet fireBullet(double power) {
        throw new UnsupportedOperationException("Not permitted");
    }    
}

