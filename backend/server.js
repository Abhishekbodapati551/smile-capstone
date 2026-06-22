const express = require('express');
const { Pool } = require('pg');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

// Database Connection
const pool = new Pool({
  user: 'admin',
  host: 'db',
  database: 'smile_db',
  password: 'password123',
  port: 5432,
});

// Initialize Table
pool.query(`
  CREATE TABLE IF NOT EXISTS video_logs (
    id SERIAL PRIMARY KEY,
    user_id TEXT,
    video_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  )
`);

// API Endpoints
app.get('/api/videos', async (req, res) => {
  const result = await pool.query('SELECT * FROM video_logs ORDER BY created_at DESC');
  res.json(result.rows);
});

app.post('/api/upload-metadata', async (req, res) => {
  const { user_id, video_url } = req.body;
  await pool.query('INSERT INTO video_logs (user_id, video_url) VALUES ($1, $2)', [user_id, video_url]);
  res.sendStatus(201);
});

app.listen(5000, () => console.log('Backend API running on port 5000'));
