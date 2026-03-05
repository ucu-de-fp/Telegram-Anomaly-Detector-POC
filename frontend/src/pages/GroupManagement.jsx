import React, { useState, useEffect } from 'react';
import { MapContainer, TileLayer, Polygon, Polyline, CircleMarker, Marker, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import { groupAPI } from '../services/api';
import './GroupManagement.css';

const DEFAULT_CENTER = [50.4501, 30.5234];
const centroidFromGroup = (group) => {
  if (!group || group.centroidLatitude == null || group.centroidLongitude == null) {
    return null;
  }
  return [group.centroidLatitude, group.centroidLongitude];
};

const centroidIcon = L.divIcon({
  className: 'notification-count-marker',
  html: '<div class="marker-pin"></div>',
  iconSize: [30, 42],
  iconAnchor: [15, 42],
  popupAnchor: [0, -36],
});

const PolygonSelector = ({ onAddPoint }) => {
  useMapEvents({
    click: (e) => {
      onAddPoint(e.latlng);
    },
  });

  return null;
};

const GroupManagement = () => {
  const [groups, setGroups] = useState([]);
  const [formData, setFormData] = useState({
    name: '',
    link: '',
    telegramGroupId: '',
    polygon: [],
  });
  const [editingId, setEditingId] = useState(null);
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [mapCenter, setMapCenter] = useState(DEFAULT_CENTER);
  const [editingCentroid, setEditingCentroid] = useState(null);

  useEffect(() => {
    loadGroups();
  }, []);

  const loadGroups = async () => {
    try {
      const response = await groupAPI.getAll();
      setGroups(response.data);
    } catch (error) {
      console.error('Error loading groups:', error);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (formData.polygon.length < 3) {
      alert('Please draw a polygon with at least 3 points.');
      return;
    }
    try {
      const parsedTelegramGroupId = Number(formData.telegramGroupId);
      if (!Number.isInteger(parsedTelegramGroupId)) {
        alert('Telegram Group ID must be a valid integer.');
        return;
      }
      const payload = {
        ...formData,
        telegramGroupId: parsedTelegramGroupId,
      };
      if (editingId) {
        await groupAPI.update(editingId, payload);
      } else {
        await groupAPI.create(payload);
      }
      loadGroups();
      resetForm();
    } catch (error) {
      console.error('Error saving group:', error);
      const errorMessage = error?.response?.data?.message;
      if (errorMessage) {
        alert(errorMessage);
      }
    }
  };

  const handleEdit = (group) => {
    setFormData({
      name: group.name,
      link: group.link,
      telegramGroupId: group.telegramGroupId ?? '',
      polygon: group.polygon || [],
    });
    setEditingId(group.id);
    const centroid = centroidFromGroup(group);
    if (centroid) {
      setMapCenter(centroid);
      setEditingCentroid(centroid);
      return;
    }
    setMapCenter(DEFAULT_CENTER);
    setEditingCentroid(null);
  };

  const handleSelectPolygon = (group) => {
    if (!group.polygon || group.polygon.length < 3) {
      return;
    }
    setSelectedGroup(group);
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this group?')) {
      try {
        await groupAPI.delete(id);
        loadGroups();
      } catch (error) {
        console.error('Error deleting group:', error);
      }
    }
  };

  const resetForm = () => {
    setFormData({
      name: '',
      link: '',
      telegramGroupId: '',
      polygon: [],
    });
    setEditingId(null);
    setMapCenter(DEFAULT_CENTER);
    setEditingCentroid(null);
  };

  const addPolygonPoint = (latlng) => {
    setFormData((prev) => ({
      ...prev,
      polygon: [...prev.polygon, { latitude: latlng.lat, longitude: latlng.lng }],
    }));
  };

  const removeLastPoint = () => {
    setFormData((prev) => ({
      ...prev,
      polygon: prev.polygon.slice(0, -1),
    }));
  };

  const clearPolygon = () => {
    setFormData((prev) => ({
      ...prev,
      polygon: [],
    }));
  };

  const polygonLatLngs = formData.polygon.map((point) => [point.latitude, point.longitude]);
  const selectedPolygonLatLngs = selectedGroup?.polygon
    ? selectedGroup.polygon.map((point) => [point.latitude, point.longitude])
    : [];
  const selectedCentroid = centroidFromGroup(selectedGroup);
  const selectedMapCenter = selectedCentroid || DEFAULT_CENTER;

  return (
    <div className="group-management">
      <h1>Group Management</h1>
      
      <form onSubmit={handleSubmit} className="group-form">
        <input
          type="text"
          placeholder="Group Name"
          value={formData.name}
          onChange={(e) => setFormData({ ...formData, name: e.target.value })}
          required
        />
        <input
          type="text"
          placeholder="Group Link"
          value={formData.link}
          onChange={(e) => setFormData({ ...formData, link: e.target.value })}
          required
        />
        <input
          type="number"
          placeholder="Telegram Group ID"
          value={formData.telegramGroupId}
          step="1"
          onChange={(e) => setFormData({ ...formData, telegramGroupId: e.target.value })}
          required
        />
        <div className="polygon-section">
          <div className="polygon-header">
            <strong>Group Area (Polygon)</strong>
            <span>{formData.polygon.length} points</span>
          </div>
          <div className="polygon-actions">
            <button type="button" onClick={removeLastPoint} disabled={formData.polygon.length === 0}>
              Undo Last
            </button>
            <button type="button" onClick={clearPolygon} disabled={formData.polygon.length === 0}>
              Clear
            </button>
          </div>
          <div className="polygon-help">
            Click on the map to add vertices. You need at least 3 points.
          </div>
          <MapContainer
            center={mapCenter}
            zoom={6}
            doubleClickZoom={false}
            className="group-map"
          >
            <TileLayer
              url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            />
            <PolygonSelector onAddPoint={addPolygonPoint} />
            {polygonLatLngs.length >= 3 && (
              <Polygon positions={polygonLatLngs} pathOptions={{ color: '#4CAF50', weight: 2, fillOpacity: 0.2 }} />
            )}
            {polygonLatLngs.length >= 2 && polygonLatLngs.length < 3 && (
              <Polyline positions={polygonLatLngs} pathOptions={{ color: '#4CAF50', weight: 2 }} />
            )}
            {polygonLatLngs.map((pos, index) => (
              <CircleMarker
                key={`${pos[0]}-${pos[1]}-${index}`}
                center={pos}
                radius={4}
                pathOptions={{ color: '#4CAF50', fillColor: '#4CAF50', fillOpacity: 0.9 }}
              />
            ))}
            {editingCentroid && (
              <Marker
                position={editingCentroid}
                icon={centroidIcon}
              />
            )}
          </MapContainer>
        </div>
        <div className="form-actions">
          <button type="submit">{editingId ? 'Update' : 'Create'}</button>
          {editingId && <button type="button" onClick={resetForm}>Cancel</button>}
        </div>
      </form>

      <table className="groups-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Link</th>
            <th>Telegram ID</th>
            <th>Centroid</th>
            <th>Polygon</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {groups.map((group) => (
            <tr key={group.id}>
              <td>{group.name}</td>
              <td>
                <a href={group.link} target="_blank" rel="noopener noreferrer">
                  {group.link}
                </a>
              </td>
              <td>{group.telegramGroupId ?? '—'}</td>
              <td>
                {group.centroidLatitude != null && group.centroidLongitude != null
                  ? `${group.centroidLatitude.toFixed(4)}, ${group.centroidLongitude.toFixed(4)}`
                  : '—'}
              </td>
              <td>
                {group.polygon && group.polygon.length > 0 ? (
                  <button
                    type="button"
                    className="polygon-link"
                    onClick={() => handleSelectPolygon(group)}
                  >
                    View ({group.polygon.length} points)
                  </button>
                ) : (
                  '—'
                )}
              </td>
              <td>
                <button onClick={() => handleEdit(group)}>Edit</button>
                <button onClick={() => handleDelete(group.id)}>Delete</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="polygon-preview">
        <div className="polygon-preview-header">
          <h2>Polygon Preview</h2>
          <span>
            {selectedGroup
              ? `${selectedGroup.name} (${selectedGroup.polygon.length} points)`
              : 'Select a group polygon from the table'}
          </span>
        </div>
        <MapContainer
          center={selectedGroup ? selectedMapCenter : [49.0, 32.0]}
          zoom={selectedGroup ? 7 : 6}
          className="group-map"
        >
          <TileLayer
            url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          />
          {selectedPolygonLatLngs.length >= 3 && (
            <Polygon
              positions={selectedPolygonLatLngs}
              pathOptions={{ color: '#4CAF50', weight: 2, fillOpacity: 0.2 }}
            />
          )}
          {selectedCentroid && (
            <Marker
              position={selectedCentroid}
              icon={centroidIcon}
            />
          )}
        </MapContainer>
      </div>
    </div>
  );
};

export default GroupManagement;
