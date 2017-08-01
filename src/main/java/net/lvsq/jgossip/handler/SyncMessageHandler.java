// Copyright (c) 2017 The jgossip Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.lvsq.jgossip.handler;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import net.lvsq.jgossip.core.GossipManager;
import net.lvsq.jgossip.core.Serializer;
import net.lvsq.jgossip.model.AckMessage;
import net.lvsq.jgossip.model.GossipDigest;
import net.lvsq.jgossip.model.GossipMember;
import net.lvsq.jgossip.model.HeartbeatState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lvsq
 */
public class SyncMessageHandler implements MessageHandler {
    @Override
    public void handle(String cluster, String data, String from) {
        if (data != null) {
            JsonArray array = new JsonArray(data);
            List<GossipDigest> olders = new ArrayList<>();
            Map<GossipMember, HeartbeatState> newers = new HashMap<>();
            for (Object e : array) {
                GossipDigest g = Serializer.getInstance().decode(Buffer.buffer().appendString(e.toString()), GossipDigest.class);
                compareDigest(g, cluster, olders, newers);
            }
            AckMessage ackMessage = new AckMessage(olders, newers);
            Buffer ackBuffer = GossipManager.getInstance().encodeAckMessage(ackMessage);
            if (from != null) {
                String[] host = from.split(":");
                GossipManager.getInstance().getSettings().getMsgService().sendMsg(host[0], Integer.valueOf(host[1]), ackBuffer);
            }
        }
    }

    private void compareDigest(GossipDigest g, String cluster, List<GossipDigest> olders, Map<GossipMember, HeartbeatState> newers) {
        GossipMember member = new GossipMember();
        member.setCluster(cluster);
        member.setIpAddress(g.getEndpoint().getAddress().getHostAddress());
        member.setPort(g.getEndpoint().getPort());
        HeartbeatState hb = GossipManager.getInstance().getEndpointMembers().get(member);
        long remoteHeartbeatTime = g.getHeartbeatTime();
        long remoteVersion = g.getVersion();
        if (hb != null) {
            long localHeartbeatTime = hb.getHeartbeatTime();
            long localVersion = hb.getVersion();

            if (remoteHeartbeatTime > localHeartbeatTime) {
                olders.add(g);
            } else if (remoteHeartbeatTime < localHeartbeatTime) {
                newers.put(member, hb);
            } else if (remoteHeartbeatTime == localHeartbeatTime) {
                if (remoteVersion > localVersion) {
                    olders.add(g);
                } else if (remoteVersion < localVersion) {
                    newers.put(member, hb);
                }
            }
        } else {
            olders.add(g);
        }
    }
}
