import React, { useState, useEffect, useRef } from 'react';
import { MapContainer, TileLayer, Rectangle, useMapEvents } from 'react-leaflet';
import { zoneAPI } from '../services/api';
import './PipelineManagement.css';

const ZERO_AREA_EPSILON = 1e-7;

const ZoneSelector = ({ setBounds }) => {
  const selectionRef = useRef({
    isSelecting: false,
    startPoint: null,
  });

  const map = useMapEvents({
    mousedown: (e) => {
      if (e.originalEvent?.button !== 0) {
        return;
      }

      selectionRef.current.isSelecting = true;
      selectionRef.current.startPoint = e.latlng;
      map.dragging.disable();
    },
    mousemove: (e) => {
      const { isSelecting, startPoint } = selectionRef.current;
      if (!isSelecting || !startPoint) {
        return;
      }

      setBounds([
        [Math.min(startPoint.lat, e.latlng.lat), Math.min(startPoint.lng, e.latlng.lng)],
        [Math.max(startPoint.lat, e.latlng.lat), Math.max(startPoint.lng, e.latlng.lng)],
      ]);
    },
    mouseup: (e) => {
      const { isSelecting, startPoint } = selectionRef.current;
      if (!isSelecting || !startPoint) {
        return;
      }

      const endPoint = e.latlng || startPoint;
      const hasArea =
        Math.abs(startPoint.lat - endPoint.lat) > ZERO_AREA_EPSILON &&
        Math.abs(startPoint.lng - endPoint.lng) > ZERO_AREA_EPSILON;

      if (hasArea) {
        setBounds([
          [Math.min(startPoint.lat, endPoint.lat), Math.min(startPoint.lng, endPoint.lng)],
          [Math.max(startPoint.lat, endPoint.lat), Math.max(startPoint.lng, endPoint.lng)],
        ]);
      }

      selectionRef.current.isSelecting = false;
      selectionRef.current.startPoint = null;
      map.dragging.enable();
    },
  });

  useEffect(() => {
    const handleWindowMouseUp = () => {
      if (!selectionRef.current.isSelecting) {
        return;
      }

      selectionRef.current.isSelecting = false;
      selectionRef.current.startPoint = null;
      map.dragging.enable();
    };

    window.addEventListener('mouseup', handleWindowMouseUp);
    return () => {
      window.removeEventListener('mouseup', handleWindowMouseUp);
      map.dragging.enable();
    };
  }, [map]);

  return null;
};

const PipelineManagement = () => {
  const [bounds, setBounds] = useState(null);
  const [savedZone, setSavedZone] = useState(null);

  useEffect(() => {
    loadActiveZone();
  }, []);

  const loadActiveZone = async () => {
    try {
      const response = await zoneAPI.getActive();
      if (response.data) {
        const zone = response.data;
        setBounds([
          [zone.minLatitude, zone.minLongitude],
          [zone.maxLatitude, zone.maxLongitude]
        ]);
        setSavedZone(zone);
      }
    } catch (error) {
      console.error('Error loading zone:', error);
    }
  };

  const saveZone = async () => {
    if (!bounds) {
      alert('Please select a zone first');
      return;
    }

    try {
      const zoneData = {
        minLatitude: bounds[0][0],
        minLongitude: bounds[0][1],
        maxLatitude: bounds[1][0],
        maxLongitude: bounds[1][1],
      };
      await zoneAPI.set(zoneData);
      alert('Zone saved successfully!');
      loadActiveZone();
    } catch (error) {
      console.error('Error saving zone:', error);
      alert('Error saving zone');
    }
  };

  const clearZone = () => {
    setBounds(null);
  };

  return (
    <div className="pipeline-management">
      <h1>Pipeline Management</h1>
      
      <div className="zone-info">
        <p>Draw a rectangle on the map to define the zone of interest</p>
        {bounds && (
          <div className="coordinates">
            <strong>Current Selection:</strong>
            <span>Min: [{bounds[0][0].toFixed(4)}, {bounds[0][1].toFixed(4)}]</span>
            <span>Max: [{bounds[1][0].toFixed(4)}, {bounds[1][1].toFixed(4)}]</span>
          </div>
        )}
        <div className="zone-actions">
          <button onClick={saveZone} disabled={!bounds}>Save Zone</button>
          <button onClick={clearZone} disabled={!bounds}>Clear Selection</button>
        </div>
      </div>

      <MapContainer
        center={[49.0, 32.0]}
        zoom={6}
        className="zone-map"
      >
        <TileLayer
          url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        />
        <ZoneSelector setBounds={setBounds} />
        {bounds && (
          <Rectangle
            bounds={bounds}
            interactive={false}
            pathOptions={{ color: '#4CAF50', weight: 2 }}
          />
        )}
      </MapContainer>
    </div>
  );
};

export default PipelineManagement;
