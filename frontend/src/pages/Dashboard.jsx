import React, { useEffect, useMemo, useRef, useState } from 'react';
import { MapContainer, TileLayer, Polygon, Polyline, CircleMarker, Marker, Popup, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import { groupAPI, notificationAPI } from '../services/api';
import './Dashboard.css';

const PolygonSelector = ({ isDrawing, onAddPoint }) => {
  useMapEvents({
    click: (e) => {
      if (!isDrawing) {
        return;
      }
      onAddPoint(e.latlng);
    },
  });

  return null;
};

const Dashboard = () => {
  const [notifications, setNotifications] = useState([]);
  const [newNotificationIds, setNewNotificationIds] = useState(new Set());
  const [filterPolygon, setFilterPolygon] = useState([]);
  const [isDrawingFilter, setIsDrawingFilter] = useState(false);
  const [groupIds, setGroupIds] = useState([]);
  const [groupsById, setGroupsById] = useState({});
  const eventSourceRef = useRef(null);

  useEffect(() => {
    refreshForPolygon(filterPolygon);
  }, [filterPolygon]);

  useEffect(() => {
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
    };
  }, []);

  const refreshForPolygon = async (polygon) => {
    try {
      const response = polygon.length >= 3
        ? await groupAPI.searchByPolygon({ polygon })
        : await groupAPI.getAll();

      const groups = response.data || [];
      const ids = Array.from(
        new Set(groups.map((group) => group.id).filter((id) => id != null))
      );
      setGroupIds(ids);
      const groupMap = {};
      groups.forEach((group) => {
        if (group?.id != null) {
          groupMap[group.id] = group;
        }
      });
      setGroupsById(groupMap);

      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }

      if (ids.length === 0) {
        setNotifications([]);
        setNewNotificationIds(new Set());
        return;
      }

      const history = await notificationAPI.searchByGroupIds(ids);
      setNotifications(history.data || []);
      setNewNotificationIds(new Set());
      connectSSE(ids);
    } catch (error) {
      console.error('Error refreshing notifications:', error);
    }
  };

  const connectSSE = (ids) => {
    if (!ids || ids.length === 0) {
      return;
    }

    const eventSource = new EventSource(notificationAPI.subscribe(ids));
    eventSourceRef.current = eventSource;

    eventSource.onmessage = (event) => {
      const notification = JSON.parse(event.data);
      console.log('Received notification:', notification);

      setNotifications((prev) => [notification, ...prev]);
      setNewNotificationIds((prev) => new Set(prev).add(notification.id));

      setTimeout(() => {
        setNewNotificationIds((prev) => {
          const next = new Set(prev);
          next.delete(notification.id);
          return next;
        });
      }, 2000);
    };

    eventSource.onerror = (error) => {
      console.error('SSE error:', error);
      eventSource.close();

      setTimeout(() => {
        console.log('Reconnecting to SSE...');
        connectSSE(ids);
      }, 5000);
    };
  };

  const addFilterPoint = (latlng) => {
    setFilterPolygon((prev) => [
      ...prev,
      { latitude: latlng.lat, longitude: latlng.lng },
    ]);
  };

  const undoFilterPoint = () => {
    setFilterPolygon((prev) => prev.slice(0, -1));
  };

  const clearFilter = () => {
    setFilterPolygon([]);
  };

  const filterPolygonLatLngs = filterPolygon.map((point) => [
    point.latitude,
    point.longitude,
  ]);

  const groupedNotifications = useMemo(() => {
    const counts = {};
    const newCounts = {};
    notifications.forEach((notification) => {
      const groupId = notification.groupId;
      if (groupId == null) {
        return;
      }
      counts[groupId] = (counts[groupId] || 0) + 1;
      if (newNotificationIds.has(notification.id)) {
        newCounts[groupId] = true;
      }
    });
    return { counts, newCounts };
  }, [notifications, newNotificationIds]);

  const markers = useMemo(() => {
    return Object.entries(groupedNotifications.counts)
      .map(([groupId, count]) => {
        const group = groupsById[groupId];
        if (!group || group.centroidLatitude == null || group.centroidLongitude == null) {
          return null;
        }
        return {
          groupId,
          count,
          group,
          isNew: Boolean(groupedNotifications.newCounts[groupId]),
        };
      })
      .filter(Boolean);
  }, [groupedNotifications, groupsById]);

  const createCountIcon = (count, isNew) => {
    return L.divIcon({
      className: `notification-count-marker${isNew ? ' new' : ''}`,
      html: `<div class="marker-pin"></div><div class="marker-count">${count}</div>`,
      iconSize: [30, 42],
      iconAnchor: [15, 42],
      popupAnchor: [0, -36],
    });
  };

  return (
    <div className="dashboard">
      <h1>Dashboard</h1>

      <div className="dashboard-content">
        <div className="map-container">
          <div className="filter-panel">
            <div className="filter-panel-header">
              <h2>Notification Filter</h2>
              <span>
                {filterPolygon.length >= 3
                  ? `${filterPolygon.length} points`
                  : 'No polygon selected'}
              </span>
            </div>
            <p>
              Draw a polygon to filter notifications by group area overlap.
              {groupIds.length > 0 && (
                <> Showing {groupIds.length} group(s).</>
              )}
              {groupIds.length === 0 && (
                <> No groups matched.</>
              )}
            </p>
            <div className="filter-actions">
              <button type="button" onClick={() => setIsDrawingFilter((prev) => !prev)}>
                {isDrawingFilter ? 'Stop Drawing' : 'Draw Filter Polygon'}
              </button>
              <button type="button" onClick={undoFilterPoint} disabled={filterPolygon.length === 0}>
                Undo Last
              </button>
              <button type="button" onClick={clearFilter} disabled={filterPolygon.length === 0}>
                Clear
              </button>
            </div>
            {isDrawingFilter && (
              <div className="filter-hint">Click on the map to add vertices (min 3).</div>
            )}
          </div>

          <MapContainer
            center={[49.0, 32.0]}
            zoom={6}
            className="notification-map"
          >
            <TileLayer
              url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            />
            <PolygonSelector isDrawing={isDrawingFilter} onAddPoint={addFilterPoint} />
            {filterPolygonLatLngs.length >= 3 && (
              <Polygon
                positions={filterPolygonLatLngs}
                pathOptions={{ color: '#ff9800', weight: 2, fillOpacity: 0.2 }}
              />
            )}
            {filterPolygonLatLngs.length >= 2 && filterPolygonLatLngs.length < 3 && (
              <Polyline positions={filterPolygonLatLngs} pathOptions={{ color: '#ff9800', weight: 2 }} />
            )}
            {filterPolygonLatLngs.map((pos, index) => (
              <CircleMarker
                key={`${pos[0]}-${pos[1]}-${index}`}
                center={pos}
                radius={4}
                pathOptions={{ color: '#ff9800', fillColor: '#ff9800', fillOpacity: 0.9 }}
              />
            ))}
            {markers.map((marker) => (
              <Marker
                key={marker.groupId}
                position={[marker.group.centroidLatitude, marker.group.centroidLongitude]}
                icon={createCountIcon(marker.count, marker.isNew)}
              >
                <Popup>
                  <div className="notification-popup">
                    <h3>{marker.group.name}</h3>
                    <p><strong>Notifications:</strong> {marker.count}</p>
                    <a href={marker.group.link} target="_blank" rel="noopener noreferrer">
                      Open Group
                    </a>
                  </div>
                </Popup>
              </Marker>
            ))}
          </MapContainer>
        </div>

        <div className="notifications-table-container">
          <h2>Recent Notifications</h2>
          <table className="notifications-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Group</th>
                <th>Keyword</th>
                <th>Content</th>
              </tr>
            </thead>
            <tbody>
              {notifications.map((notif) => (
                <tr
                  key={notif.id}
                  className={newNotificationIds.has(notif.id) ? 'new-notification' : ''}
                >
                  <td>{new Date(notif.timestamp).toLocaleTimeString()}</td>
                  <td>
                    {(() => {
                      const group = groupsById[notif.groupId];
                      const groupName = group?.name || notif.groupName || (notif.groupId != null ? `Group ${notif.groupId}` : 'Unknown group');
                      const groupLink = group?.link || notif.groupLink;
                      if (!groupLink) {
                        return groupName;
                      }
                      return (
                        <a href={groupLink} target="_blank" rel="noopener noreferrer">
                          {groupName}
                        </a>
                      );
                    })()}
                  </td>
                  <td><span className="keyword-badge">{notif.keyword}</span></td>
                  <td>{notif.content}</td>
                </tr>
              ))}
              {notifications.length === 0 && (
                <tr>
                  <td colSpan="4">No notifications found.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
